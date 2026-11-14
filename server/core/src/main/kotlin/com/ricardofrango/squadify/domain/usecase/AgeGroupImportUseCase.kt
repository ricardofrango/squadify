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
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.domain.stats.MatchPlayerStatsCalculator
import com.ricardofrango.squadify.exception.ConflictException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.AgeGroupBundle
import com.ricardofrango.squadify.model.AgeGroupPlayer
import com.ricardofrango.squadify.model.ImportAgeGroupResult
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchCallUp
import com.ricardofrango.squadify.model.MatchEvent
import com.ricardofrango.squadify.model.MatchReport
import com.ricardofrango.squadify.model.Opponent
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.Season
import com.ricardofrango.squadify.model.Team
import com.ricardofrango.squadify.model.birthYearsOverlap
import java.text.Normalizer
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Copies an age group from another server into [targetClubId] (TECHNICAL_DECISIONS §81). One transaction: a refusal
 * writes nothing. The importer must be allowed to edit the target club ([AccessPolicy.requireEditClub]). Every
 * record gets a new id here and is remembered by its source id, so an update can find it later.
 *
 * Matching: a player with the same name and birth date in the target is reused; an opponent with the same name
 * (ignoring case and accents) is reused; a season with the source's dates is reused. Staff are not copied.
 *
 * Refused: an age group already imported from this source; a birth-year clash in the target season; a plan
 * without room (the coach server); a team size the target's sport doesn't play.
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
    private val now: () -> Instant = { Clock.System.now() }
) {
    suspend operator fun invoke(userId: String, targetClubId: String, sourceServer: String, bundle: AgeGroupBundle): ImportAgeGroupResult =
        transactionManager.runInTransaction {
            accessPolicy.requireEditClub(userId, targetClubId, "Only club admins or coordinators can import an age group")
            val club = clubRepository.getClubById(targetClubId) ?: throw NotFoundException("Club not found")

            importRepository.findAgeGroup(targetClubId, sourceServer, bundle.ageGroup.id)?.let {
                throw ConflictException("This age group was already imported from there")
            }

            // The season: the one with the source's dates, found or made here.
            val sourceSeason = bundle.season
            val season = seasonRepository.findByDetails(targetClubId, sourceSeason.type, sourceSeason.startDate, sourceSeason.endDate)
                ?: seasonRepository.createSeason(
                    Season(Uuid.random().toString(), targetClubId, sourceSeason.name, sourceSeason.type, sourceSeason.startDate, sourceSeason.endDate, active = true)
                )
            accessPolicy.requireRoomForAgeGroups(targetClubId, season.id, 1)

            // A birth-year clash in the target season is refused, as the age-group wizard does.
            val group = bundle.ageGroup
            val clash = ageGroupRepository.findByClubAndSeason(targetClubId, season.id).firstOrNull { other ->
                accessPolicy.shareBirthYears(group.name, other.name) && birthYearsOverlap(group.startYear, group.endYear, other.startYear, other.endYear)
            }
            if (clash != null) {
                throw ConflictException("The club already has \"${clash.name}\" for these birth years this season")
            }

            val sportSizes = club.sport.teamSizes
            fun sizeFor(teamSize: Int?): Int? = teamSize?.also {
                if (it !in sportSizes) throw ValidationException("Team size $it is not played in ${club.sport}")
            }

            val ageGroupId = Uuid.random().toString()
            ageGroupRepository.createAgeGroup(
                AgeGroup(
                    id = ageGroupId,
                    clubId = targetClubId,
                    name = group.name,
                    seasonId = season.id,
                    startYear = group.startYear,
                    endYear = group.endYear,
                    teamSize = sizeFor(group.teamSize),
                    logoUrl = null
                )
            )
            importRepository.saveAgeGroupLink(targetClubId, sourceServer, group.id, ageGroupId)

            // Teams.
            val teamIds = HashMap<String, String>()
            val teams = bundle.teams.map { t ->
                val id = Uuid.random().toString()
                teamIds[t.id] = id
                importRepository.saveRecord(targetClubId, sourceServer, group.id, KIND_TEAM, t.id, id)
                Team(id, targetClubId, ageGroupId, season.id, t.name, t.code, t.active, sizeFor(t.teamSize))
            }
            teamRepository.createTeams(teams)

            // Players: the same name and birth date in the club is the same person.
            val existingPlayers = playerRepository.getPlayersByClub(targetClubId).associateBy { playerKey(it.firstName, it.lastName, it.birthDate.toString()) }
            val playerIds = HashMap<String, String>()
            val newPlayers = mutableListOf<Player>()
            for (p in bundle.players) {
                val existing = existingPlayers[playerKey(p.firstName, p.lastName, p.birthDate.toString())]
                val id = existing?.id ?: Uuid.random().toString().also {
                    newPlayers += Player(it, targetClubId, p.firstName, p.lastName, p.birthDate, p.shirtNumber, null, p.active, p.position, p.heightCm, p.weightKg, p.emergencyContact)
                }
                playerIds[p.id] = id
                importRepository.saveRecord(targetClubId, sourceServer, group.id, KIND_PLAYER, p.id, id)
            }
            if (newPlayers.isNotEmpty()) playerRepository.createPlayers(newPlayers)
            val links = bundle.ageGroupPlayers.mapNotNull { link ->
                playerIds[link.playerId]?.let { AgeGroupPlayer(Uuid.random().toString(), ageGroupId, it) }
            }
            if (links.isNotEmpty()) playerRepository.addPlayersToAgeGroup(links)

            // Opponents: the same name in the club is the same club.
            val existingOpponents = opponentRepository.getOpponentsByClub(targetClubId).associateBy { normalized(it.name) }
            val opponentIds = HashMap<String, String>()
            var newOpponents = 0
            for (o in bundle.opponents) {
                val existing = existingOpponents[normalized(o.name)]
                val id = existing?.id ?: Uuid.random().toString().also {
                    newOpponents++
                    opponentRepository.createOpponent(
                        Opponent(it, targetClubId, o.name, o.shortName, null, o.stadiumName, o.stadiumAddress, o.contactEmail, o.contactPhone, o.notes)
                    )
                }
                opponentIds[o.id] = id
                importRepository.saveRecord(targetClubId, sourceServer, group.id, KIND_OPPONENT, o.id, id)
            }

            // Matches: new ids throughout, so events and the call-ups they refer to are mapped first.
            val matchIds = HashMap<String, String>()
            val eventIds = HashMap<String, String>()
            bundle.matches.forEach { m ->
                matchIds[m.id] = Uuid.random().toString()
                m.events.forEach { e -> eventIds[e.id] = Uuid.random().toString() }
            }
            for (m in bundle.matches) {
                val matchId = matchIds.getValue(m.id)
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

                val callUps = m.callUps.mapNotNull { c ->
                    playerIds[c.playerId]?.let { MatchCallUp(Uuid.random().toString(), matchId, it, c.status, c.notes, c.isStarting, c.pitchSlot) }
                }
                callUps.forEach { matchRepository.createCallUp(it) }

                m.lineupChanges.forEach { change ->
                    val slots = change.slots.mapNotNull { (slot, sourcePlayer) -> playerIds[sourcePlayer]?.let { slot to it } }.toMap()
                    matchRepository.addLineupChange(matchId, change.copy(slots = slots))
                }

                val events = m.events.map { e ->
                    MatchEvent(
                        id = eventIds.getValue(e.id),
                        matchId = matchId,
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
                events.forEach { eventRepository.addEvent(it) }

                // A finished match's player lines are worked out as the source did, then its ratings are put back.
                if (m.fullTimeMinute != null) {
                    val lines = MatchPlayerStatsCalculator.calculate(match, callUps, events)
                    statsRepository.replaceForMatch(matchId, lines)
                    val ratings = m.ratings.mapNotNull { r -> playerIds[r.playerId]?.let { it to r.rating } }.toMap()
                    if (ratings.isNotEmpty()) statsRepository.updateRatings(matchId, ratings)
                }

                m.report?.let { r ->
                    reportRepository.upsert(
                        MatchReport(
                            matchId = matchId,
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

            ImportAgeGroupResult(
                ageGroupId = ageGroupId,
                teams = teams.size,
                players = newPlayers.size,
                opponents = newOpponents,
                matches = bundle.matches.size
            )
        }

    private companion object {
        const val KIND_TEAM = "TEAM"
        const val KIND_PLAYER = "PLAYER"
        const val KIND_OPPONENT = "OPPONENT"
        const val KIND_MATCH = "MATCH"

        fun normalized(name: String): String =
            Normalizer.normalize(name.trim(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase()

        fun playerKey(firstName: String, lastName: String, birthDate: String): String =
            "${normalized(firstName)} ${normalized(lastName)}|$birthDate"
    }
}
