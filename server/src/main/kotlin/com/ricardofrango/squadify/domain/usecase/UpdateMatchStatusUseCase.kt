package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.MatchEventRepository
import com.ricardofrango.squadify.domain.repository.MatchPlayerStatsRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.service.ClubMatchesBus
import com.ricardofrango.squadify.domain.service.LiveMatchBus
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.domain.stats.MatchPlayerStatsCalculator
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.*
import kotlin.time.Instant

// Kickoff (SCHEDULED -> LIVE) only ever happens through StartMatchUseCase, which also locks in the
// starting line-up -- allowing it here too would let a caller skip that requirement entirely, so it's
// deliberately absent below. FINISHED and CANCELLED are terminal: nothing transitions out of either.
private val VALID_MATCH_STATUS_TRANSITIONS: Map<MatchStatus, Set<MatchStatus>> = mapOf(
    MatchStatus.SCHEDULED to setOf(MatchStatus.CANCELLED),
    MatchStatus.LIVE to setOf(MatchStatus.HALF_TIME, MatchStatus.FINISHED, MatchStatus.CANCELLED),
    MatchStatus.HALF_TIME to setOf(MatchStatus.LIVE, MatchStatus.FINISHED, MatchStatus.CANCELLED),
    MatchStatus.FINISHED to emptySet(),
    MatchStatus.CANCELLED to emptySet()
)

class UpdateMatchStatusUseCase(
    private val matchRepository: MatchRepository,
    private val matchEventRepository: MatchEventRepository,
    private val matchPlayerStatsRepository: MatchPlayerStatsRepository,
    private val liveMatchBus: LiveMatchBus,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val notifyMatchEventUseCase: NotifyMatchEventUseCase,
    private val clubMatchesBus: ClubMatchesBus,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(
        userId: String,
        matchId: String,
        status: MatchStatus
    ): Boolean {
        var notificationEvent: MatchNotificationEvent? = null
        var updatedMatch: Match? = null

        val success = transactionManager.runInTransaction {
            // 1. Verify match exists first to get context
            val match = matchRepository.getMatchById(matchId)
                ?: throw NotFoundException("Match not found")

            // 2. Single permission check (Fail Fast) using match's clubId and teamId
            checkUserPermissionUseCase(
                userId = userId,
                clubId = match.clubId,
                minClubRole = ClubRole.COORDINATOR,
                configurablePermission = ConfigurablePermission.MANAGE_LIVE_MATCH,
                teamId = match.teamId,
                errorMessage = "Only admins, coordinators, coaches, or team staff can update match status"
            )

            if (status !in (VALID_MATCH_STATUS_TRANSITIONS[match.status] ?: emptySet())) {
                throw ValidationException("Cannot change match status from ${match.status} to $status")
            }

            val updated = matchRepository.updateMatchStatus(matchId, status)
            if (updated) {
                // Resuming for the second half -- record when, so the client's running clock can pick up
                // from the first half's length instead of restarting at 0 (see Match.secondHalfStartedAt).
                val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
                var refetchedMatch = match.copy(status = status)
                if (status == MatchStatus.LIVE && match.status == MatchStatus.HALF_TIME) {
                    matchRepository.markSecondHalfStarted(matchId, now)
                    refetchedMatch = refetchedMatch.copy(secondHalfStartedAt = now)
                    notificationEvent = MatchNotificationEvent.SECOND_HALF_STARTED
                } else if (status == MatchStatus.HALF_TIME) {
                    // Same floor(seconds / 60) unit as event minutes, so stoppage time is included.
                    val minute = elapsedMatchSeconds(match, now) / 60
                    matchRepository.markFirstHalfEnded(matchId, minute)
                    refetchedMatch = refetchedMatch.copy(firstHalfEndMinute = minute)
                    notificationEvent = MatchNotificationEvent.HALF_TIME
                } else if (status == MatchStatus.FINISHED) {
                    // Ended from the break (no second half): the clock stopped when the first half did.
                    // Otherwise read it now.
                    val minute = if (match.status == MatchStatus.HALF_TIME) {
                        match.firstHalfEndMinute ?: (match.halfLengthMinutes ?: 0)
                    } else {
                        elapsedMatchSeconds(match, now) / 60
                    }
                    matchRepository.markFullTime(matchId, minute)
                    refetchedMatch = refetchedMatch.copy(fullTimeMinute = minute)
                    // Freeze each called-up player's line now -- events can't change after full time.
                    val stats = MatchPlayerStatsCalculator.calculate(
                        match = refetchedMatch,
                        callUps = matchRepository.getCallUpsForMatch(matchId).map { it.callUp },
                        events = matchEventRepository.getEventsByMatch(matchId)
                    )
                    matchPlayerStatsRepository.replaceForMatch(matchId, stats)
                    notificationEvent = MatchNotificationEvent.FULL_TIME
                } else if (status == MatchStatus.CANCELLED) {
                    notificationEvent = MatchNotificationEvent.CANCELLED
                }
                // Built from the already-loaded `match` rather than re-fetched, since a MatchRepository
                // call made outside this transaction (i.e. after runInTransaction returns) throws. Carries
                // every field just written above (secondHalfStartedAt, the half/full-time minutes), not
                // just status -- otherwise every listener driving its clock off this broadcast (the club
                // Matches list included) keeps working from stale state until its next full refetch.
                updatedMatch = refetchedMatch
                val score = matchEventRepository.getScoreByMatch(matchId)
                liveMatchBus.publish(
                    matchId = matchId,
                    update = LiveMatchUpdate(score = score, match = refetchedMatch)
                )
            }
            updated
        }

        // Sent after the transaction commits -- a failed/slow push must never roll back the status change.
        if (success) {
            notificationEvent?.let { notifyMatchEventUseCase(matchId, it) }
            updatedMatch?.let { clubMatchesBus.publish(it.clubId, ClubMatchesUpdate(match = it)) }
        }
        return success
    }
}
