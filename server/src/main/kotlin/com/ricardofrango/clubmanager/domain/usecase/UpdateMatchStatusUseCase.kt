package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.MatchEventRepository
import com.ricardofrango.clubmanager.domain.repository.MatchRepository
import com.ricardofrango.clubmanager.domain.service.LiveMatchBus
import com.ricardofrango.clubmanager.domain.service.TransactionManager
import com.ricardofrango.clubmanager.exception.NotFoundException
import com.ricardofrango.clubmanager.model.*
import kotlin.time.Instant

class UpdateMatchStatusUseCase(
    private val matchRepository: MatchRepository,
    private val matchEventRepository: MatchEventRepository,
    private val liveMatchBus: LiveMatchBus,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(
        userId: String,
        matchId: String,
        status: MatchStatus
    ): Boolean = transactionManager.runInTransaction {
        // 1. Verify match exists first to get context
        val match = matchRepository.getMatchById(matchId)
            ?: throw NotFoundException("Match not found")

        // 2. Single permission check (Fail Fast) using match's clubId and teamId
        checkUserPermissionUseCase(
            userId = userId,
            clubId = match.clubId,
            minTeamRole = TeamRole.COACH,
            teamId = match.teamId,
            errorMessage = "Only admins, coordinators, or team coaches can update match status"
        )

        val success = matchRepository.updateMatchStatus(matchId, status)
        if (success) {
            // Resuming for the second half -- record when, so the client's running clock can pick up
            // from the first half's length instead of restarting at 0 (see Match.secondHalfStartedAt).
            if (status == MatchStatus.LIVE && match.status == MatchStatus.HALF_TIME) {
                matchRepository.markSecondHalfStarted(matchId, Instant.fromEpochMilliseconds(System.currentTimeMillis()))
            }
            val score = matchEventRepository.getScoreByMatch(matchId)
            liveMatchBus.publish(
                matchId = matchId,
                update = LiveMatchUpdate(score = score, status = status)
            )
        }
        success
    }
}
