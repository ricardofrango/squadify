package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.access.AccessPolicy
import com.ricardofrango.squadify.domain.access.allowed
import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.ExportGrantRepository
import com.ricardofrango.squadify.domain.repository.MatchEventRepository
import com.ricardofrango.squadify.domain.repository.MatchPlayerStatsRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.MatchReportRepository
import com.ricardofrango.squadify.domain.repository.OpponentRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.SeasonRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.TrainingExerciseRepository
import com.ricardofrango.squadify.domain.repository.TrainingPlanRepository
import com.ricardofrango.squadify.domain.repository.TrainingRatingRepository
import com.ricardofrango.squadify.domain.repository.TrainingSessionRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.UnauthorizedException
import com.ricardofrango.squadify.model.AgeGroupBundle
import com.ricardofrango.squadify.model.BundleAgeGroup
import com.ricardofrango.squadify.model.BundleAgeGroupPlayer
import com.ricardofrango.squadify.model.BundleCallUp
import com.ricardofrango.squadify.model.BundleExercise
import com.ricardofrango.squadify.model.BundleSession
import com.ricardofrango.squadify.model.BundleSessionRating
import com.ricardofrango.squadify.model.BundleEvent
import com.ricardofrango.squadify.model.BundleMatch
import com.ricardofrango.squadify.model.BundleOpponent
import com.ricardofrango.squadify.model.BundlePlayer
import com.ricardofrango.squadify.model.BundlePlayerRating
import com.ricardofrango.squadify.model.BundleReport
import com.ricardofrango.squadify.model.BundleSeason
import com.ricardofrango.squadify.model.BundleTeam
import com.ricardofrango.squadify.model.ExportGrantResponse
import com.ricardofrango.squadify.model.ExportableAgeGroup
import com.ricardofrango.squadify.model.MatchStatus
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.math.roundToInt
import java.util.Base64
import kotlin.time.Instant

/** The export grant's token: random, shown once; only its SHA-256 is stored (TECHNICAL_DECISIONS §81). */
object ExportGrantTokens {
    /** Five minutes: a grant is for one import started now, not for later. */
    const val LIFETIME_MILLIS = 5 * 60 * 1000L

    private val random = SecureRandom()

    fun newToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun hash(token: String): String =
        MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

/** Creates an export grant for the signed-in user (the app asks for one when the user allows the import). */
class CreateExportGrantUseCase(
    private val grants: ExportGrantRepository,
    private val transactionManager: TransactionManager,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    suspend operator fun invoke(userId: String): ExportGrantResponse = transactionManager.runInTransaction {
        val token = ExportGrantTokens.newToken()
        val expiresAt = clock() + ExportGrantTokens.LIFETIME_MILLIS
        grants.create(ExportGrantTokens.hash(token), userId, expiresAt)
        ExportGrantResponse(token, Instant.fromEpochMilliseconds(expiresAt))
    }
}

/**
 * The age groups a grant's user may copy out of this server: the ones in the clubs they may edit (the clubs
 * server: admins and coordinators; the coach server: the owner, whose space is their only one to edit).
 * Using the grant here doesn't use it up: the bundle does.
 */
class ListExportableAgeGroupsUseCase(
    private val grants: ExportGrantRepository,
    private val clubRepository: ClubRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val seasonRepository: SeasonRepository,
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    suspend operator fun invoke(token: String): List<ExportableAgeGroup> = transactionManager.runInTransaction {
        val userId = grants.userOf(ExportGrantTokens.hash(token), clock()) ?: throw UnauthorizedException("The export grant is invalid or expired")
        // Memberships only (club_members): the coach database has no parents' links, and the access check decides the rest.
        clubRepository.getMemberships(userId).flatMap { (club, _) ->
            val mayExport = allowed { accessPolicy.requireEditClub(userId, club.id, EXPORT_MESSAGE) }
            if (!mayExport) return@flatMap emptyList()
            ageGroupRepository.getAgeGroupsByClub(club.id).map { ageGroup ->
                ExportableAgeGroup(
                    id = ageGroup.id,
                    clubId = club.id,
                    clubName = club.name,
                    name = ageGroup.name,
                    startYear = ageGroup.startYear,
                    endYear = ageGroup.endYear,
                    seasonName = seasonRepository.getSeasonById(ageGroup.seasonId)?.name.orEmpty()
                )
            }
        }
    }
}

/**
 * One age group, as a bundle (TECHNICAL_DECISIONS §81): its season, teams, the players in it (and any others its
 * matches use), the opponents its matches use, and its matches that aren't in progress, with call-ups, line-ups,
 * events, ratings and the report. The grant is used up here, so it can't be used twice. Read only: nothing changes
 * on this server.
 */
class ExportAgeGroupUseCase(
    private val grants: ExportGrantRepository,
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager,
    private val ageGroupRepository: AgeGroupRepository,
    private val seasonRepository: SeasonRepository,
    private val teamRepository: TeamRepository,
    private val playerRepository: PlayerRepository,
    private val opponentRepository: OpponentRepository,
    private val matchRepository: MatchRepository,
    private val eventRepository: MatchEventRepository,
    private val reportRepository: MatchReportRepository,
    private val statsRepository: MatchPlayerStatsRepository,
    private val trainingSessions: TrainingSessionRepository,
    private val trainingExercises: TrainingExerciseRepository,
    private val trainingRatings: TrainingRatingRepository,
    private val trainingPlans: TrainingPlanRepository,
    private val sourceServer: String,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    suspend operator fun invoke(token: String, ageGroupId: String): AgeGroupBundle {
        return transactionManager.runInTransaction {
            val userId = grants.redeem(ExportGrantTokens.hash(token), clock()) ?: throw UnauthorizedException("The export grant is invalid, expired or used")
            val ageGroup = ageGroupRepository.getAgeGroupById(ageGroupId) ?: throw NotFoundException("Age group not found")
            accessPolicy.requireEditClub(userId, ageGroup.clubId, EXPORT_MESSAGE)
            val season = seasonRepository.getSeasonById(ageGroup.seasonId) ?: throw NotFoundException("Season not found")
            val teams = teamRepository.getTeamsByClub(ageGroup.clubId).filter { it.ageGroupId == ageGroup.id }
            val inGroup = playerRepository.getPlayersByAgeGroup(ageGroup.id)
            val matches = teams.flatMap { matchRepository.getMatchesByTeam(it.id) }
                .filter { it.status != MatchStatus.LIVE && it.status != MatchStatus.HALF_TIME }

            val players = LinkedHashMap<String, BundlePlayer>()
            inGroup.forEach { players[it.id] = it.toBundle() }
            val bundleMatches = matches.map { match ->
                val callUps = matchRepository.getCallUpsForMatch(match.id)
                callUps.forEach { p -> players.getOrPut(p.player.id) { p.player.toBundle() } }
                val events = eventRepository.getEventsByMatch(match.id)
                events.mapNotNull { it.playerId }.distinct().forEach { id ->
                    if (id !in players) playerRepository.getPlayerById(id)?.let { players[id] = it.toBundle() }
                }
                val stats = statsRepository.getByMatch(match.id)
                val report = reportRepository.getByMatch(match.id)
                BundleMatch(
                    id = match.id,
                    teamId = match.teamId,
                    opponentId = match.opponentId,
                    location = match.location,
                    dateTime = match.dateTime,
                    status = match.status,
                    notes = match.notes,
                    matchType = match.type,
                    halves = match.halves,
                    venue = match.venue,
                    halfLengthMinutes = match.halfLengthMinutes,
                    kickoffAt = match.kickoffAt,
                    secondHalfStartedAt = match.secondHalfStartedAt,
                    firstHalfEndMinute = match.firstHalfEndMinute,
                    fullTimeMinute = match.fullTimeMinute,
                    formation = match.formation,
                    startingFormation = match.startingFormation,
                    enteredAfterGame = match.enteredAfterGame,
                    sport = match.sport,
                    teamSize = match.teamSize,
                    callUps = callUps.map { it.callUp.toBundle() },
                    lineupChanges = matchRepository.getLineupChanges(match.id),
                    events = events.map { it.toBundle() },
                    ratings = stats.map { BundlePlayerRating(it.playerId, it.rating) },
                    report = report?.toBundle()
                )
            }

            val opponents = matches.map { it.opponentId }.distinct().mapNotNull { opponentRepository.getOpponentById(it) }

            // Training: the exercise library, each session with its exercises in order and its ratings, and the plans.
            val exercises = trainingExercises.getByAgeGroup(ageGroup.id)
            val stored = trainingSessions.getByAgeGroup(ageGroup.id)
            val sessionIds = stored.map { it.session.id }
            val ratingsBySession = trainingRatings.getForSessions(sessionIds)
            val sessions = stored.map { row ->
                val s = row.session
                val ratings = ratingsBySession[s.id].orEmpty().map { r ->
                    if (r.playerId !in players) playerRepository.getPlayerById(r.playerId)?.let { players[r.playerId] = it.toBundle() }
                    BundleSessionRating(r.playerId, (r.rating * 100).roundToInt())
                }
                BundleSession(
                    id = s.id,
                    date = s.date,
                    startTime = s.startTime,
                    endTime = s.endTime,
                    location = s.location,
                    notes = s.notes,
                    cancelledAt = s.cancelledAt,
                    exerciseIds = trainingExercises.getForSession(s.id).map { it.id },
                    ratings = ratings
                )
            }
            val plans = trainingPlans.getByAgeGroup(ageGroup.id)

            AgeGroupBundle(
                sourceServer = sourceServer,
                season = BundleSeason(season.id, season.name, season.type, season.startDate, season.endDate),
                ageGroup = BundleAgeGroup(ageGroup.id, ageGroup.name, ageGroup.startYear, ageGroup.endYear, ageGroup.teamSize, ageGroup.logoUrl),
                teams = teams.map { BundleTeam(it.id, it.name, it.code, it.active, it.teamSize) },
                players = players.values.toList(),
                ageGroupPlayers = inGroup.map { BundleAgeGroupPlayer(it.id) },
                opponents = opponents.map { it.toBundle() },
                matches = bundleMatches,
                exercises = exercises.map { BundleExercise(it.id, it.name, it.description, it.durationMinutes, it.objectives) },
                sessions = sessions,
                plans = plans
            )
        }
    }
}

private const val EXPORT_MESSAGE = "Only club admins or coordinators can copy an age group out of the club"

private fun com.ricardofrango.squadify.model.Player.toBundle() = BundlePlayer(
    id, firstName, lastName, birthDate, shirtNumber, photoUrl, active, position, heightCm, weightKg, emergencyContact
)

private fun com.ricardofrango.squadify.model.MatchCallUp.toBundle() = BundleCallUp(id, playerId, status, notes, isStarting, pitchSlot)

private fun com.ricardofrango.squadify.model.MatchEvent.toBundle() = BundleEvent(
    id, type, minute, playerId, opponentPlayerName, isOpponent, period, relatedEventId, createdAt
)

private fun com.ricardofrango.squadify.model.MatchReport.toBundle() = BundleReport(
    tactic, positiveAspects, lessPositiveAspects, aspectsToImprove, teamNotes, opponentTactic, opponentStrengths,
    opponentWeaknesses, opponentBestPlayers, opponentAdditionalInfo, completedAt
)

private fun com.ricardofrango.squadify.model.Opponent.toBundle() = BundleOpponent(
    id, name, shortName, logoUrl, stadiumName, stadiumAddress, contactEmail, contactPhone, notes
)
