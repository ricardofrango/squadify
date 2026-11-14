package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.access.AccessPolicy
import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.ImportRepository
import com.ricardofrango.squadify.domain.repository.MatchEventRepository
import com.ricardofrango.squadify.domain.repository.MatchPlayerStatsRepository
import com.ricardofrango.squadify.domain.repository.MatchReportRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.OpponentRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.SeasonRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.TrainingExerciseRepository
import com.ricardofrango.squadify.domain.repository.TrainingPlanRepository
import com.ricardofrango.squadify.domain.repository.TrainingRatingRepository
import com.ricardofrango.squadify.domain.repository.TrainingSessionRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.domain.stats.MatchPlayerStatsCalculator
import com.ricardofrango.squadify.exception.ConflictException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.AgeGroupBundle
import com.ricardofrango.squadify.model.AgeGroupPlayer
import com.ricardofrango.squadify.model.BundleMatch
import com.ricardofrango.squadify.model.ImportAgeGroupResult
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchCallUp
import com.ricardofrango.squadify.model.MatchEvent
import com.ricardofrango.squadify.model.MatchReport
import com.ricardofrango.squadify.model.Opponent
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.Season
import com.ricardofrango.squadify.model.Team
import com.ricardofrango.squadify.model.TrainingExercise
import com.ricardofrango.squadify.model.TrainingSession
import com.ricardofrango.squadify.model.birthYearsOverlap
import java.text.Normalizer
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Copies an age group from another server into [targetClubId] (TECHNICAL_DECISIONS §81). One transaction: a refusal
 * writes nothing. The importer must be allowed to edit the target club ([AccessPolicy.requireEditClub]).
 *
 * Every source record is found through import_records: one already there is updated to the source's version; one
 * not there is created here and remembered. So a first import creates everything, and importing again updates the
 * copy: what came from the source takes its current version, what was created only here stays, and what was deleted
 * at the source stays here. A match's line-up history is replaced with the source's (its changes have no ids).
 *
 * Matching, for a first import: a player with the same name and birth date in the club is reused, an opponent with
 * the same name (ignoring case and accents) is reused, and a season with the source's dates is reused.
 *
 * Refused: a birth-year clash in the target season (a first import); a plan without room (the coach server); a team
 * size the target's sport doesn't play.
 */
class ImportAgeGroupUseCase(
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager,
    private val importRepository: ImportRepository,
    private val clubRepository: ClubRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val seasonRepository: SeasonRepository,
    private val teamRepository: TeamRepository,
    private val playerRepository: PlayerRepository,
    private val opponentRepository: OpponentRepository,
    private val matchRepository: MatchRepository,
    private val eventRepository: MatchEventRepository,
    private val reportRepository: MatchReportRepository,
    private val statsRepository: MatchPlayerStatsRepository,
    private val trainingExercises: TrainingExerciseRepository,
    private val trainingSessions: TrainingSessionRepository,
    private val trainingRatings: TrainingRatingRepository,
    private val trainingPlans: TrainingPlanRepository,
    private val now: () -> Instant = { Clock.System.now() }
) {
    suspend operator fun invoke(userId: String, targetClubId: String, sourceServer: String, bundle: AgeGroupBundle): ImportAgeGroupResult =
        transactionManager.runInTransaction {
            accessPolicy.requireEditClub(userId, targetClubId, "Only club admins or coordinators can import an age group")
            val club = clubRepository.getClubById(targetClubId) ?: throw NotFoundException("Club not found")
            val sizes = club.sport.teamSizes
            fun sizeFor(teamSize: Int?): Int? = teamSize?.also {
                if (it !in sizes) throw ValidationException("Team size $it is not played in ${club.sport}")
            }

            val group = bundle.ageGroup
            val linkedAgeGroupId = importRepository.findAgeGroup(targetClubId, sourceServer, group.id)
            val ageGroupId = if (linkedAgeGroupId == null) {
                createAgeGroup(userId, targetClubId, sourceServer, bundle, ::sizeFor)
            } else {
                val existing = ageGroupRepository.getAgeGroupById(linkedAgeGroupId) ?: throw NotFoundException("The imported age group is gone")
                ageGroupRepository.updateAgeGroup(
                    existing.copy(name = group.name, startYear = group.startYear, endYear = group.endYear, teamSize = sizeFor(group.teamSize))
                )
                linkedAgeGroupId
            }
            val seasonId = ageGroupRepository.getAgeGroupById(ageGroupId)!!.seasonId

            // Teams.
            val teamIds = HashMap<String, String>()
            var newTeams = 0
            for (t in bundle.teams) {
                val mapped = importRepository.findRecord(targetClubId, sourceServer, group.id, KIND_TEAM, t.id)
                val team = Team(mapped ?: Uuid.random().toString(), targetClubId, ageGroupId, seasonId, t.name, t.code, t.active, sizeFor(t.teamSize))
                if (mapped != null) {
                    teamRepository.updateTeam(team)
                } else {
                    teamRepository.createTeams(listOf(team))
                    importRepository.saveRecord(targetClubId, sourceServer, group.id, KIND_TEAM, t.id, team.id)
                    newTeams++
                }
                teamIds[t.id] = team.id
            }

            // Players: the record, else the same person in the club, else a new player.
            val playerIds = HashMap<String, String>()
            var newPlayers = 0
            val byKey = playerRepository.getPlayersByClub(targetClubId).associateBy { playerKey(it.firstName, it.lastName, it.birthDate.toString()) }
            for (p in bundle.players) {
                val mapped = importRepository.findRecord(targetClubId, sourceServer, group.id, KIND_PLAYER, p.id)
                if (mapped != null) {
                    playerRepository.updatePlayer(
                        Player(mapped, targetClubId, p.firstName, p.lastName, p.birthDate, p.shirtNumber, null, p.active, p.position, p.heightCm, p.weightKg, p.emergencyContact)
                    )
                    playerIds[p.id] = mapped
                    continue
                }
                val same = byKey[playerKey(p.firstName, p.lastName, p.birthDate.toString())]
                if (same != null) {
                    playerIds[p.id] = same.id
                } else {
                    val player = Player(Uuid.random().toString(), targetClubId, p.firstName, p.lastName, p.birthDate, p.shirtNumber, null, p.active, p.position, p.heightCm, p.weightKg, p.emergencyContact)
                    playerRepository.createPlayers(listOf(player))
                    playerIds[p.id] = player.id
                    newPlayers++
                }
                importRepository.saveRecord(targetClubId, sourceServer, group.id, KIND_PLAYER, p.id, playerIds.getValue(p.id))
            }
            val inGroup = playerRepository.getPlayersByAgeGroup(ageGroupId).map { it.id }.toSet()
            val links = bundle.ageGroupPlayers.mapNotNull { link ->
                playerIds[link.playerId]?.takeIf { it !in inGroup }?.let { AgeGroupPlayer(Uuid.random().toString(), ageGroupId, it) }
            }
            if (links.isNotEmpty()) playerRepository.addPlayersToAgeGroup(links)

            // Opponents: the record, else the same name in the club, else a new opponent.
            val opponentIds = HashMap<String, String>()
            var newOpponents = 0
            val byName = opponentRepository.getOpponentsByClub(targetClubId).associateBy { normalized(it.name) }
            for (o in bundle.opponents) {
                val mapped = importRepository.findRecord(targetClubId, sourceServer, group.id, KIND_OPPONENT, o.id)
                val opponent = Opponent(mapped ?: Uuid.random().toString(), targetClubId, o.name, o.shortName, null, o.stadiumName, o.stadiumAddress, o.contactEmail, o.contactPhone, o.notes)
                val id = when {
                    mapped != null -> mapped.also { opponentRepository.updateOpponent(opponent) }
                    byName[normalized(o.name)] != null -> byName.getValue(normalized(o.name)).id
                    else -> opponent.id.also {
                        opponentRepository.createOpponent(opponent)
                        newOpponents++
                    }
                }
                opponentIds[o.id] = id
                if (mapped == null) importRepository.saveRecord(targetClubId, sourceServer, group.id, KIND_OPPONENT, o.id, id)
            }

            // Matches: each with its call-ups, line-ups, events, ratings and report.
            // Source event id -> target event id, and whether the target already has it (an update).
            val eventIds = HashMap<String, String>()
            val knownEvents = HashSet<String>()
            bundle.matches.forEach { m ->
                m.events.forEach { e ->
                    val mapped = importRepository.findRecord(targetClubId, sourceServer, group.id, KIND_EVENT, e.id)
                    if (mapped != null) knownEvents += e.id
                    eventIds[e.id] = mapped ?: Uuid.random().toString()
                }
            }
            var newMatches = 0
            for (m in bundle.matches) {
                val mapped = importRepository.findRecord(targetClubId, sourceServer, group.id, KIND_MATCH, m.id)
                val matchId = mapped ?: Uuid.random().toString()
                val match = Match(
                    id = matchId,
                    clubId = targetClubId,
                    teamId = teamIds[m.teamId] ?: throw ValidationException("A match refers to a team not in the bundle"),
                    opponentId = opponentIds[m.opponentId] ?: throw ValidationException("A match refers to an opponent not in the bundle"),
                    location = m.location,
                    dateTime = m.dateTime,
                    status = m.status,
                    notes = m.notes,
                    type = m.matchType,
                    halves = m.halves,
                    halfLengthMinutes = m.halfLengthMinutes,
                    kickoffAt = m.kickoffAt,
                    secondHalfStartedAt = m.secondHalfStartedAt,
                    firstHalfEndMinute = m.firstHalfEndMinute,
                    fullTimeMinute = m.fullTimeMinute,
                    venue = m.venue,
                    formation = m.formation,
                    startingFormation = m.startingFormation,
                    enteredAfterGame = m.enteredAfterGame,
                    teamSize = sizeFor(m.teamSize) ?: m.teamSize,
                    sport = club.sport
                )
                if (mapped != null) {
                    matchRepository.updateMatch(match)
                } else {
                    matchRepository.createMatch(match)
                    importRepository.saveRecord(targetClubId, sourceServer, group.id, KIND_MATCH, m.id, matchId)
                    newMatches++
                }
                writeMatchContents(m, match, targetClubId, sourceServer, group.id, userId, playerIds, eventIds, knownEvents)
            }

            // Training: the exercise library, then each session with its exercises in order and its ratings, then the plans.
            if (bundle.exercises.isNotEmpty() || bundle.sessions.isNotEmpty() || bundle.plans.isNotEmpty()) {
                accessPolicy.requireTrainingInPlan(targetClubId)
            }
            for (e in bundle.exercises) {
                val mapped = importRepository.findRecord(targetClubId, sourceServer, group.id, KIND_EXERCISE, e.id)
                val exercise = TrainingExercise(mapped ?: Uuid.random().toString(), ageGroupId, e.name, e.description, e.durationMinutes, e.objectives)
                if (mapped != null) {
                    trainingExercises.update(exercise)
                } else {
                    trainingExercises.create(exercise)
                    importRepository.saveRecord(targetClubId, sourceServer, group.id, KIND_EXERCISE, e.id, exercise.id)
                }
            }
            val exerciseIds = bundle.exercises.associate { e ->
                e.id to importRepository.findRecord(targetClubId, sourceServer, group.id, KIND_EXERCISE, e.id)!!
            }
            for (s in bundle.sessions) {
                val mapped = importRepository.findRecord(targetClubId, sourceServer, group.id, KIND_SESSION, s.id)
                val sessionId = mapped ?: Uuid.random().toString()
                val session = TrainingSession(
                    id = sessionId,
                    ageGroupId = ageGroupId,
                    date = s.date,
                    startTime = s.startTime,
                    endTime = s.endTime,
                    location = s.location,
                    notes = s.notes
                )
                if (mapped != null) {
                    trainingSessions.update(session)
                } else {
                    trainingSessions.insertAll(listOf(session))
                    importRepository.saveRecord(targetClubId, sourceServer, group.id, KIND_SESSION, s.id, sessionId)
                }
                // A session cancelled at the source is cancelled here, by the importer.
                trainingSessions.setCancelled(sessionId, s.cancelledAt, if (s.cancelledAt != null) userId else null)
                trainingExercises.setForSession(sessionId, s.exerciseIds.mapNotNull { exerciseIds[it] })
                val ratings = s.ratings.mapNotNull { r -> playerIds[r.playerId]?.let { it to r.hundredths } }.toMap()
                if (ratings.isNotEmpty()) trainingRatings.save(sessionId, ratings, userId)
            }
            for (p in bundle.plans) {
                val mapped = importRepository.findRecord(targetClubId, sourceServer, group.id, KIND_PLAN, p.id)
                val plan = p.copy(id = mapped ?: Uuid.random().toString(), ageGroupId = ageGroupId)
                trainingPlans.save(plan, userId)
                if (mapped == null) importRepository.saveRecord(targetClubId, sourceServer, group.id, KIND_PLAN, p.id, plan.id)
            }

            ImportAgeGroupResult(
                ageGroupId = ageGroupId,
                teams = newTeams,
                players = newPlayers,
                opponents = newOpponents,
                matches = newMatches
            )
        }

    /** The age group of a first import: its season, its birth years (a clash is refused), and its link. */
    private suspend fun createAgeGroup(
        userId: String,
        targetClubId: String,
        sourceServer: String,
        bundle: AgeGroupBundle,
        sizeFor: (Int?) -> Int?
    ): String {
        val source = bundle.season
        val season = seasonRepository.findByDetails(targetClubId, source.type, source.startDate, source.endDate)
            ?: seasonRepository.createSeason(
                Season(Uuid.random().toString(), targetClubId, source.name, source.type, source.startDate, source.endDate, active = true)
            )
        accessPolicy.requireRoomForAgeGroups(targetClubId, season.id, 1)
        val group = bundle.ageGroup
        val clash = ageGroupRepository.findByClubAndSeason(targetClubId, season.id).firstOrNull { other ->
            accessPolicy.shareBirthYears(group.name, other.name) && birthYearsOverlap(group.startYear, group.endYear, other.startYear, other.endYear)
        }
        if (clash != null) throw ConflictException("The club already has \"${clash.name}\" for these birth years this season")
        val id = Uuid.random().toString()
        ageGroupRepository.createAgeGroup(
            AgeGroup(id, targetClubId, group.name, season.id, group.startYear, group.endYear, sizeFor(group.teamSize), logoUrl = null)
        )
        importRepository.saveAgeGroupLink(targetClubId, sourceServer, group.id, id)
        return id
    }

    /**
     * A match's contents from the source: call-ups and line-ups (the line-ups replaced), events and the report. A
     * finished match's player lines are worked out again, and its ratings put back.
     */
    private suspend fun writeMatchContents(
        m: BundleMatch,
        match: Match,
        targetClubId: String,
        sourceServer: String,
        sourceAgeGroupId: String,
        userId: String,
        playerIds: Map<String, String>,
        eventIds: Map<String, String>,
        knownEvents: Set<String>
    ) {
        val callUps = m.callUps.mapNotNull { c ->
            val player = playerIds[c.playerId] ?: return@mapNotNull null
            val mapped = importRepository.findRecord(targetClubId, sourceServer, sourceAgeGroupId, KIND_CALL_UP, c.id)
            val callUp = MatchCallUp(mapped ?: Uuid.random().toString(), match.id, player, c.status, c.notes, c.isStarting, c.pitchSlot)
            if (mapped != null) {
                matchRepository.updateCallUp(callUp)
            } else {
                matchRepository.createCallUp(callUp)
                importRepository.saveRecord(targetClubId, sourceServer, sourceAgeGroupId, KIND_CALL_UP, c.id, callUp.id)
            }
            callUp
        }

        matchRepository.deleteLineupChanges(match.id)
        m.lineupChanges.forEach { change ->
            val slots = change.slots.mapNotNull { (slot, sourcePlayer) -> playerIds[sourcePlayer]?.let { slot to it } }.toMap()
            matchRepository.addLineupChange(match.id, change.copy(slots = slots))
        }

        val events = m.events.map { e ->
            MatchEvent(
                id = eventIds.getValue(e.id),
                matchId = match.id,
                type = e.type,
                minute = e.minute,
                playerId = e.playerId?.let { playerIds[it] },
                opponentPlayerName = e.opponentPlayerName,
                isOpponent = e.isOpponent,
                createdAt = e.createdAt,
                period = e.period,
                relatedEventId = e.relatedEventId?.let { eventIds[it] }
            )
        }
        m.events.zip(events).forEach { (source, event) ->
            if (source.id in knownEvents) {
                eventRepository.updateEvent(event)
            } else {
                eventRepository.addEvent(event)
                importRepository.saveRecord(targetClubId, sourceServer, sourceAgeGroupId, KIND_EVENT, source.id, event.id)
            }
        }

        if (m.fullTimeMinute != null) {
            val lines = MatchPlayerStatsCalculator.calculate(match, callUps, events)
            statsRepository.replaceForMatch(match.id, lines)
            val ratings = m.ratings.mapNotNull { r -> playerIds[r.playerId]?.let { it to r.rating } }.toMap()
            if (ratings.isNotEmpty()) statsRepository.updateRatings(match.id, ratings)
        }

        m.report?.let { r ->
            reportRepository.upsert(
                MatchReport(
                    matchId = match.id,
                    tactic = r.tactic,
                    positiveAspects = r.positiveAspects,
                    lessPositiveAspects = r.lessPositiveAspects,
                    aspectsToImprove = r.aspectsToImprove,
                    teamNotes = r.teamNotes,
                    opponentTactic = r.opponentTactic,
                    opponentStrengths = r.opponentStrengths,
                    opponentWeaknesses = r.opponentWeaknesses,
                    opponentBestPlayers = r.opponentBestPlayers,
                    opponentAdditionalInfo = r.opponentAdditionalInfo,
                    completedAt = r.completedAt,
                    updatedAt = now(),
                    updatedByUserId = userId
                )
            )
        }
    }

    private companion object {
        const val KIND_TEAM = "TEAM"
        const val KIND_PLAYER = "PLAYER"
        const val KIND_OPPONENT = "OPPONENT"
        const val KIND_MATCH = "MATCH"
        const val KIND_CALL_UP = "CALL_UP"
        const val KIND_EVENT = "EVENT"
        const val KIND_EXERCISE = "EXERCISE"
        const val KIND_SESSION = "SESSION"
        const val KIND_PLAN = "PLAN"

        fun normalized(name: String): String =
            Normalizer.normalize(name.trim(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase()

        fun playerKey(firstName: String, lastName: String, birthDate: String): String =
            "${normalized(firstName)} ${normalized(lastName)}|$birthDate"
    }
}
