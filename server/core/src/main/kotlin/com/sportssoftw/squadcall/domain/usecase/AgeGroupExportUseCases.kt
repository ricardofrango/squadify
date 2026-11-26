package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.access.AccessPolicy
import com.sportssoftw.squadcall.domain.access.allowed
import com.sportssoftw.squadcall.domain.repository.AgeGroupRepository
import com.sportssoftw.squadcall.domain.repository.ClubRepository
import com.sportssoftw.squadcall.domain.repository.ExportGrantRepository
import com.sportssoftw.squadcall.domain.repository.MatchEventRepository
import com.sportssoftw.squadcall.domain.repository.MatchPlayerStatsRepository
import com.sportssoftw.squadcall.domain.repository.MatchRepository
import com.sportssoftw.squadcall.domain.repository.MatchReportRepository
import com.sportssoftw.squadcall.domain.repository.OpponentRepository
import com.sportssoftw.squadcall.domain.repository.PlayerRepository
import com.sportssoftw.squadcall.domain.repository.SeasonRepository
import com.sportssoftw.squadcall.domain.repository.TeamRepository
import com.sportssoftw.squadcall.domain.repository.TrainingExerciseRepository
import com.sportssoftw.squadcall.domain.repository.TrainingPlanRepository
import com.sportssoftw.squadcall.domain.repository.TrainingRatingRepository
import com.sportssoftw.squadcall.domain.repository.TrainingSessionRepository
import com.sportssoftw.squadcall.domain.service.ImageStorage
import com.sportssoftw.squadcall.domain.service.TransactionManager
import com.sportssoftw.squadcall.exception.NotFoundException
import com.sportssoftw.squadcall.exception.UnauthorizedException
import com.sportssoftw.squadcall.model.AgeGroupBundle
import com.sportssoftw.squadcall.model.BundleAgeGroup
import com.sportssoftw.squadcall.model.BundleImage
import com.sportssoftw.squadcall.model.BundlePlayerMinutes
import com.sportssoftw.squadcall.model.BundleAgeGroupPlayer
import com.sportssoftw.squadcall.model.BundleCallUp
import com.sportssoftw.squadcall.model.BundleExercise
import com.sportssoftw.squadcall.model.BundleSession
import com.sportssoftw.squadcall.model.BundleSessionRating
import com.sportssoftw.squadcall.model.BundleEvent
import com.sportssoftw.squadcall.model.BundleMatch
import com.sportssoftw.squadcall.model.BundleOpponent
import com.sportssoftw.squadcall.model.BundlePlayer
import com.sportssoftw.squadcall.model.BundlePlayerRating
import com.sportssoftw.squadcall.model.BundleReport
import com.sportssoftw.squadcall.model.BundleSeason
import com.sportssoftw.squadcall.model.BundleTeam
import com.sportssoftw.squadcall.model.ExportGrantResponse
import com.sportssoftw.squadcall.model.ExportableAgeGroup
import com.sportssoftw.squadcall.model.MatchStatus
import java.security.MessageDigest
import java.util.Base64
import java.security.SecureRandom
import kotlin.math.roundToInt
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
            ageGroupRepository.getAgeGroupsByClub(club.id).mapNotNull { ageGroup ->
                val season = seasonRepository.getSeasonById(ageGroup.seasonId) ?: return@mapNotNull null
                ExportableAgeGroup(
                    id = ageGroup.id,
                    clubId = club.id,
                    clubName = club.name,
                    name = ageGroup.name,
                    startYear = ageGroup.startYear,
                    endYear = ageGroup.endYear,
                    seasonName = season.name,
                    sport = club.sport,
                    seasonType = season.type,
                    seasonStartDate = season.startDate,
                    seasonEndDate = season.endDate
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
    private val imageStorage: ImageStorage,
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
            inGroup.forEach { players[it.id] = playerBundle(it) }
            val bundleMatches = matches.map { match ->
                val callUps = matchRepository.getCallUpsForMatch(match.id)
                callUps.forEach { p -> if (p.player.id !in players) players[p.player.id] = playerBundle(p.player) }
                val events = eventRepository.getEventsByMatch(match.id)
                (events.mapNotNull { it.playerId } + events.mapNotNull { it.goalkeeperPlayerId }).distinct().forEach { id ->
                    if (id !in players) playerRepository.getPlayerById(id)?.let { players[id] = playerBundle(it) }
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
                    report = report?.toBundle(),
                    minutes = stats.map { BundlePlayerMinutes(it.playerId, it.started, it.minutesPlayed) }
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
                    if (r.playerId !in players) playerRepository.getPlayerById(r.playerId)?.let { players[r.playerId] = playerBundle(it) }
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
                ageGroup = BundleAgeGroup(ageGroup.id, ageGroup.name, ageGroup.startYear, ageGroup.endYear, ageGroup.teamSize, ageGroup.logoUrl, logo = ageGroup.logoUrl?.let { AgeGroupLogos.keyOf(ageGroup.id, it) }?.let { image(it) }),
                teams = teams.map { BundleTeam(it.id, it.name, it.code, it.active, it.teamSize) },
                players = players.values.toList(),
                ageGroupPlayers = inGroup.map { BundleAgeGroupPlayer(it.id) },
                opponents = opponents.map { opponentBundle(it) },
                matches = bundleMatches,
                exercises = exercises.map { BundleExercise(it.id, it.name, it.description, it.durationMinutes, it.objectives) },
                sessions = sessions,
                plans = plans
            )
        }
    }

    /** A player with its photo, read from storage, so the bundle carries the file. */
    private suspend fun playerBundle(p: com.sportssoftw.squadcall.model.Player): BundlePlayer =
        p.toBundle(photo = p.photoUrl?.let { PlayerPhotos.keyOf(p.id, it) }?.let { image(it) })

    private suspend fun opponentBundle(o: com.sportssoftw.squadcall.model.Opponent): BundleOpponent =
        o.toBundle(logo = o.logoUrl?.let { OpponentLogos.keyOf(o.id, it) }?.let { image(it) })

    /** The file at [key] as a bundle image, or null when there's none. */
    private suspend fun image(key: String): BundleImage? =
        imageStorage.get(key)?.let { BundleImage("image/png", Base64.getEncoder().encodeToString(it)) }

}

private const val EXPORT_MESSAGE = "Only club admins or coordinators can copy an age group out of the club"

private fun com.sportssoftw.squadcall.model.Player.toBundle(photo: BundleImage?) = BundlePlayer(
    id, firstName, lastName, birthDate, shirtNumber, photoUrl, active, position, heightCm, weightKg, emergencyContact, photo
)

private fun com.sportssoftw.squadcall.model.MatchCallUp.toBundle() = BundleCallUp(id, playerId, status, notes, isStarting, pitchSlot)

private fun com.sportssoftw.squadcall.model.MatchEvent.toBundle() = BundleEvent(
    id, type, minute, playerId, opponentPlayerName, isOpponent, period, relatedEventId, createdAt, goalkeeperPlayerId
)

private fun com.sportssoftw.squadcall.model.MatchReport.toBundle() = BundleReport(
    tactic, positiveAspects, lessPositiveAspects, aspectsToImprove, teamNotes, opponentTactic, opponentStrengths,
    opponentWeaknesses, opponentBestPlayers, opponentAdditionalInfo, completedAt
)

private fun com.sportssoftw.squadcall.model.Opponent.toBundle(logo: BundleImage?) = BundleOpponent(
    id, name, shortName, logoUrl, stadiumName, stadiumAddress, contactEmail, contactPhone, notes, logo
)
