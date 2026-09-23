package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.MatchEventRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.service.ClubMatchesBus
import com.ricardofrango.squadify.domain.service.LiveMatchBus
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.ClubMatchesUpdate
import com.ricardofrango.squadify.model.LiveMatchUpdate
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.TeamRole
import kotlin.time.Instant

/**
 * Un-freezes the running clock after [PauseMatchUseCase]. Shifts whichever of `kickoffAt`/
 * `secondHalfStartedAt` is currently driving [com.ricardofrango.squadify.model.elapsedMatchSeconds]
 * forward by the paused duration, so the clock continues from where it paused rather than jumping
 * forward by however long the stoppage lasted -- see `Match.pausedAt`'s doc comment. No separate
 * "total paused time" field needed: shifting the reference timestamp bakes the pause duration in
 * permanently and clearing `pausedAt` is enough.
 */
class ResumeMatchUseCase(
    private val matchRepository: MatchRepository,
    private val matchEventRepository: MatchEventRepository,
    private val liveMatchBus: LiveMatchBus,
    private val clubMatchesBus: ClubMatchesBus,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, matchId: String): Boolean = transactionManager.runInTransaction {
        val match = matchRepository.getMatchById(matchId) ?: throw NotFoundException("Match not found")

        checkUserPermissionUseCase(
            userId = userId,
            clubId = match.clubId,
            minTeamRole = TeamRole.STAFF,
            teamId = match.teamId,
            errorMessage = "Only admins, coordinators, coaches, or team staff can resume a match"
        )

        if (match.status != MatchStatus.LIVE) {
            throw ValidationException("Can only resume a match that is currently live")
        }
        val pausedAt = match.pausedAt ?: throw ValidationException("Match is not paused")

        val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        val pauseDuration = now - pausedAt
        val secondHalfStart = match.secondHalfStartedAt
        val newKickoffAt: Instant
        val newSecondHalfStartedAt: Instant?
        if (secondHalfStart != null) {
            // Second half is the active reference -- kickoffAt is historical and untouched.
            newKickoffAt = match.kickoffAt ?: now
            newSecondHalfStartedAt = secondHalfStart + pauseDuration
        } else {
            // Still in the first half -- kickoffAt itself is the active reference.
            newKickoffAt = (match.kickoffAt ?: now) + pauseDuration
            newSecondHalfStartedAt = null
        }

        val resumed = matchRepository.resumeMatch(matchId, newKickoffAt, newSecondHalfStartedAt)
        if (resumed) {
            val updatedMatch = match.copy(
                kickoffAt = newKickoffAt,
                secondHalfStartedAt = newSecondHalfStartedAt,
                pausedAt = null
            )
            val score = matchEventRepository.getScoreByMatch(matchId)
            liveMatchBus.publish(matchId, LiveMatchUpdate(score = score, match = updatedMatch))
            clubMatchesBus.publish(match.clubId, ClubMatchesUpdate(match = updatedMatch))
        }
        resumed
    }
}
