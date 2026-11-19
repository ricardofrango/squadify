package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.access.AccessPolicy
import com.sportssoftw.squadify.domain.access.allowed
import com.sportssoftw.squadify.domain.repository.AgeGroupRepository
import com.sportssoftw.squadify.domain.repository.ClubRepository
import com.sportssoftw.squadify.domain.repository.ImportRepository
import com.sportssoftw.squadify.domain.repository.MatchEventRepository
import com.sportssoftw.squadify.domain.repository.MatchPlayerStatsRepository
import com.sportssoftw.squadify.domain.repository.MatchReportRepository
import com.sportssoftw.squadify.domain.repository.MatchRepository
import com.sportssoftw.squadify.domain.repository.OpponentRepository
import com.sportssoftw.squadify.domain.repository.PlayerRepository
import com.sportssoftw.squadify.domain.repository.SeasonRepository
import com.sportssoftw.squadify.domain.repository.TeamRepository
import com.sportssoftw.squadify.domain.repository.TrainingExerciseRepository
import com.sportssoftw.squadify.domain.repository.TrainingPlanRepository
import com.sportssoftw.squadify.domain.repository.TrainingRatingRepository
import com.sportssoftw.squadify.domain.repository.TrainingSessionRepository
import com.sportssoftw.squadify.domain.service.ImageStorage
import com.sportssoftw.squadify.domain.service.TransactionManager
import com.sportssoftw.squadify.domain.stats.MatchPlayerStatsCalculator
import com.sportssoftw.squadify.exception.ConflictException
import com.sportssoftw.squadify.exception.NotFoundException
import com.sportssoftw.squadify.exception.ValidationException
import com.sportssoftw.squadify.model.AgeGroup
import com.sportssoftw.squadify.model.BundleImage
import com.sportssoftw.squadify.model.AgeGroupBundle
import com.sportssoftw.squadify.model.AgeGroupPlayer
import com.sportssoftw.squadify.model.BundleMatch
import com.sportssoftw.squadify.model.ExportableAgeGroup
import com.sportssoftw.squadify.model.ImportAgeGroupResult
import com.sportssoftw.squadify.model.ImportBlock
import com.sportssoftw.squadify.model.ImportableAgeGroup
import com.sportssoftw.squadify.model.Match
import com.sportssoftw.squadify.model.MatchCallUp
import com.sportssoftw.squadify.model.MatchEvent
import com.sportssoftw.squadify.model.MatchReport
import com.sportssoftw.squadify.model.Opponent
import com.sportssoftw.squadify.model.Player
import com.sportssoftw.squadify.model.Team
import com.sportssoftw.squadify.model.TrainingExercise
import com.sportssoftw.squadify.model.TrainingSession
import com.sportssoftw.squadify.model.birthYearsOverlap
import java.text.Normalizer
import java.util.Base64
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Copies an age group from another server into [targetClubId] (TECHNICAL_DECISIONS §81). One transaction: a refusal
 * writes nothing. The importer must be allowed to edit the target club ([AccessPolicy.requireEditClub]).
 *
 * An age group is copied once: importing it again is refused. The copy's records are still found through
 * import_records, so a source record is created here once and its links are kept for the rest of the import.
 *
 * Matching: a player with the same name and birth date in the club is reused, an opponent with the same name
 * (ignoring case and accents) is reused, and a season with the source's dates is reused.
 *
 * Refused: a birth-year clash in the target season; a plan without room (the coach server); a team size the target's
 * sport doesn't play.
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
    private val imageStorage: ImageStorage,
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
            // An age group is copied once: importing it again is refused (the list shows it as imported).
            if (importRepository.findAgeGroup(targetClubId, sourceServer, group.id) != null) {
                throw ConflictException("This age group is already imported into this club")
            }
            // A copy deleted since an earlier import left its records behind: they point at rows that are gone, so they're
            // forgotten first and every row below is written new.
            importRepository.forgetRecords(targetClubId, sourceServer, group.id)
            val ageGroupId = createAgeGroup(userId, targetClubId, sourceServer, bundle, ::sizeFor)
            val seasonId = ageGroupRepository.getAgeGroupById(ageGroupId)!!.seasonId

            // Teams.
            val teamIds = HashMap<String, String>()
            var newTeams = 0
            for (t in bundle.teams) {
                val team = Team(Uuid.random().toString(), targetClubId, ageGroupId, seasonId, t.name, t.code, t.active, sizeFor(t.teamSize))
                teamRepository.createTeams(listOf(team))
                importRepository.saveRecord(targetClubId, sourceServer, group.id, KIND_TEAM, t.id, team.id)
                newTeams++
                teamIds[t.id] = team.id
            }

            // Players: the same person in the club, else a new player.
            val playerIds = HashMap<String, String>()
            var newPlayers = 0
            val byKey = playerRepository.getPlayersByClub(targetClubId).associateBy { playerKey(it.firstName, it.lastName, it.birthDate.toString()) }
            for (p in bundle.players) {
                val same = byKey[playerKey(p.firstName, p.lastName, p.birthDate.toString())]
                if (same != null) {
                    playerIds[p.id] = same.id
                } else {
                    val id = Uuid.random().toString()
                    val photo = p.photo?.let { putImage(PlayerPhotos, id, it, null) }
                    val player = Player(id, targetClubId, p.firstName, p.lastName, p.birthDate, p.shirtNumber, photo, p.active, p.position, p.heightCm, p.weightKg, p.emergencyContact)
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

            // Opponents: the same name in the club, else a new opponent.
            val opponentIds = HashMap<String, String>()
            var newOpponents = 0
            val byName = opponentRepository.getOpponentsByClub(targetClubId).associateBy { normalized(it.name) }
            for (o in bundle.opponents) {
                val existing = byName[normalized(o.name)]
                val id = if (existing != null) {
                    existing.id
                } else {
                    val opponentId = Uuid.random().toString()
                    val logo = o.logo?.let { putImage(OpponentLogos, opponentId, it, null) }
                    opponentRepository.createOpponent(
                        Opponent(opponentId, targetClubId, o.name, o.shortName, logo, o.stadiumName, o.stadiumAddress, o.contactEmail, o.contactPhone, o.notes)
                    )
                    newOpponents++
                    opponentId
                }
                opponentIds[o.id] = id
                importRepository.saveRecord(targetClubId, sourceServer, group.id, KIND_OPPONENT, o.id, id)
            }

            // Matches: each with its call-ups, line-ups, events, ratings and report.
            // Source event id -> target event id: made first, because an assist points at its goal.
            val eventIds = HashMap<String, String>()
            bundle.matches.forEach { m -> m.events.forEach { e -> eventIds[e.id] = Uuid.random().toString() } }
            var newMatches = 0
            for (m in bundle.matches) {
                val matchId = Uuid.random().toString()
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
                matchRepository.createMatch(match)
                importRepository.saveRecord(targetClubId, sourceServer, group.id, KIND_MATCH, m.id, matchId)
                newMatches++
                writeMatchContents(m, match, targetClubId, sourceServer, group.id, userId, playerIds, eventIds)
            }

            // Training: the exercise library, then each session with its exercises in order and its ratings, then the plans.
            // Not on the club's plan (a Free or Basic space): the age group is still copied, without its training, and the
            // result says so.
            val trainingAllowed = allowed { accessPolicy.requireTrainingInPlan(targetClubId) }
            val hasTraining = bundle.exercises.isNotEmpty() || bundle.sessions.isNotEmpty() || bundle.plans.isNotEmpty()
            val trainingSkipped = hasTraining && !trainingAllowed
            val exercises = if (trainingAllowed) bundle.exercises else emptyList()
            val sessions = if (trainingAllowed) bundle.sessions else emptyList()
            val plans = if (trainingAllowed) bundle.plans else emptyList()
            val exerciseIds = HashMap<String, String>()
            for (e in exercises) {
                val exercise = TrainingExercise(Uuid.random().toString(), ageGroupId, e.name, e.description, e.durationMinutes, e.objectives)
                trainingExercises.create(exercise)
                importRepository.saveRecord(targetClubId, sourceServer, group.id, KIND_EXERCISE, e.id, exercise.id)
                exerciseIds[e.id] = exercise.id
            }
            for (s in sessions) {
                val sessionId = Uuid.random().toString()
                val session = TrainingSession(
                    id = sessionId,
                    ageGroupId = ageGroupId,
                    date = s.date,
                    startTime = s.startTime,
                    endTime = s.endTime,
                    location = s.location,
                    notes = s.notes
                )
                trainingSessions.insertAll(listOf(session))
                importRepository.saveRecord(targetClubId, sourceServer, group.id, KIND_SESSION, s.id, sessionId)
                // A session cancelled at the source is cancelled here, by the importer.
                trainingSessions.setCancelled(sessionId, s.cancelledAt, if (s.cancelledAt != null) userId else null)
                trainingExercises.setForSession(sessionId, s.exerciseIds.mapNotNull { exerciseIds[it] })
                val ratings = s.ratings.mapNotNull { r -> playerIds[r.playerId]?.let { it to r.hundredths } }.toMap()
                if (ratings.isNotEmpty()) trainingRatings.save(sessionId, ratings, userId)
            }
            for (p in plans) {
                val plan = p.copy(id = Uuid.random().toString(), ageGroupId = ageGroupId)
                trainingPlans.save(plan, userId)
                importRepository.saveRecord(targetClubId, sourceServer, group.id, KIND_PLAN, p.id, plan.id)
            }

            ImportAgeGroupResult(
                ageGroupId = ageGroupId,
                teams = newTeams,
                players = newPlayers,
                opponents = newOpponents,
                matches = newMatches,
                trainingSkipped = trainingSkipped
            )
        }

    /**
     * Stores a copied image under [ownerId] in this server's storage, replacing [previousUrl]'s file. Its URL is
     * returned; a server without image storage can't copy the image, so the import is refused rather than losing it.
     */
    private suspend fun putImage(paths: ImagePaths, ownerId: String, image: BundleImage, previousUrl: String?): String {
        if (!imageStorage.isAvailable) throw ValidationException("This server has no image storage, so the photos and logos can't be copied")
        val imageId = Uuid.random().toString()
        imageStorage.put(paths.key(ownerId, imageId), Base64.getDecoder().decode(image.base64), image.contentType)
        previousUrl?.let { paths.keyOf(ownerId, it) }?.let { imageStorage.delete(it) }
        return paths.url(ownerId, imageId)
    }

    /** The age group of a first import: its season, its birth years (a clash is refused), and its link. */
    private suspend fun createAgeGroup(
        userId: String,
        targetClubId: String,
        sourceServer: String,
        bundle: AgeGroupBundle,
        sizeFor: (Int?) -> Int?
    ): String {
        // Only the club's own season: an age group from another season (other dates) is never copied in, and no season is made for it.
        val source = bundle.season
        val season = seasonRepository.findByDetails(targetClubId, source.type, source.startDate, source.endDate)
            ?: throw ConflictException("This age group is from ${source.name}, which isn't this club's season, so it can't be copied in")
        accessPolicy.requireRoomForAgeGroups(targetClubId, season.id, 1)
        val group = bundle.ageGroup
        val clash = ageGroupRepository.findByClubAndSeason(targetClubId, season.id).firstOrNull { other ->
            accessPolicy.shareBirthYears(group.name, other.name) && birthYearsOverlap(group.startYear, group.endYear, other.startYear, other.endYear)
        }
        if (clash != null) {
            throw ConflictException(
                "The club already has \"${clash.name}\" for birth years ${clash.startYear}–${clash.endYear} this season, so this age group can't be copied in"
            )
        }
        val id = Uuid.random().toString()
        val logo = group.logo?.let { putImage(AgeGroupLogos, id, it, null) }
        ageGroupRepository.createAgeGroup(
            AgeGroup(id, targetClubId, group.name, season.id, group.startYear, group.endYear, sizeFor(group.teamSize), logoUrl = logo)
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
        eventIds: Map<String, String>
    ) {
        val callUps = m.callUps.mapNotNull { c ->
            val player = playerIds[c.playerId] ?: return@mapNotNull null
            val callUp = MatchCallUp(Uuid.random().toString(), match.id, player, c.status, c.notes, c.isStarting, c.pitchSlot)
            matchRepository.createCallUp(callUp)
            importRepository.saveRecord(targetClubId, sourceServer, sourceAgeGroupId, KIND_CALL_UP, c.id, callUp.id)
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
                relatedEventId = e.relatedEventId?.let { eventIds[it] },
                goalkeeperPlayerId = e.goalkeeperPlayerId?.let { playerIds[it] }
            )
        }
        // An assist points at its goal (relatedEventId), which must be written first: events without a related event
        // go in before the ones that have one.
        m.events.zip(events).sortedBy { (source, _) -> source.relatedEventId != null }.forEach { (source, event) ->
            eventRepository.addEvent(event)
            importRepository.saveRecord(targetClubId, sourceServer, sourceAgeGroupId, KIND_EVENT, source.id, event.id)
        }

        if (m.fullTimeMinute != null) {
            // The source's stored minutes win over the ones worked out here: for a result typed in after the game they
            // aren't in any line-up or substitution. A source that sent none leaves them as worked out.
            val stored = m.minutes.mapNotNull { r -> playerIds[r.playerId]?.let { it to r } }.toMap()
            val lines = MatchPlayerStatsCalculator.calculate(match, callUps, events).map { line ->
                stored[line.playerId]?.let { line.copy(started = it.started, minutesPlayed = it.minutesPlayed) } ?: line
            }
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

/**
 * The age groups another server offers ([offered], read with the grant), each with why this club can't copy it now,
 * when it can't (TECHNICAL_DECISIONS §81): already imported, another sport, no room in the plan, or a birth-year clash
 * in the same season. Read only: nothing is copied.
 */
class ListImportableAgeGroupsUseCase(
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager,
    private val importRepository: ImportRepository,
    private val clubRepository: ClubRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val seasonRepository: SeasonRepository
) {
    suspend operator fun invoke(
        userId: String,
        targetClubId: String,
        sourceServer: String,
        offered: List<ExportableAgeGroup>
    ): List<ImportableAgeGroup> = transactionManager.runInTransaction {
        accessPolicy.requireEditClub(userId, targetClubId, "Only club admins or coordinators can import an age group")
        val club = clubRepository.getClubById(targetClubId) ?: throw NotFoundException("Club not found")
        val linked = importRepository.linkedAgeGroups(targetClubId, sourceServer)
        offered.map { a ->
            val season = seasonRepository.findByDetails(targetClubId, a.seasonType, a.seasonStartDate, a.seasonEndDate)
            val clash = season?.let { s ->
                ageGroupRepository.findByClubAndSeason(targetClubId, s.id).firstOrNull { other ->
                    accessPolicy.shareBirthYears(a.name, other.name) && birthYearsOverlap(a.startYear, a.endYear, other.startYear, other.endYear)
                }
            }
            val room = season != null && allowed { accessPolicy.requireRoomForAgeGroups(targetClubId, season.id, 1) }
            val blocked = when {
                a.id in linked -> ImportBlock.ALREADY_IMPORTED
                a.sport != club.sport -> ImportBlock.OTHER_SPORT
                season == null -> ImportBlock.OTHER_SEASON
                !room -> ImportBlock.NO_ROOM_IN_PLAN
                clash != null -> ImportBlock.BIRTH_YEARS_TAKEN
                else -> null
            }
            val shown = if (blocked == ImportBlock.BIRTH_YEARS_TAKEN) clash else null
            ImportableAgeGroup(
                id = a.id,
                clubName = a.clubName,
                name = a.name,
                startYear = a.startYear,
                endYear = a.endYear,
                seasonName = a.seasonName,
                sport = a.sport,
                blocked = blocked,
                clashName = shown?.name,
                clashStartYear = shown?.startYear,
                clashEndYear = shown?.endYear
            )
        }
    }
}
