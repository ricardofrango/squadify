package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.MatchEventRepository
import com.ricardofrango.footballmanager.domain.repository.MatchRepository
import com.ricardofrango.footballmanager.exception.NotFoundException
import com.ricardofrango.footballmanager.model.LiveMatchUpdate
import com.ricardofrango.footballmanager.model.MatchStatus
import com.ricardofrango.footballmanager.model.Role
import com.ricardofrango.footballmanager.security.MatchSessionManager

class UpdateMatchStatusUseCase(
    private val matchRepository: MatchRepository,
    private val matchEventRepository: MatchEventRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase
) {
    suspend operator fun invoke(
        userId: String,
        matchId: String,
        status: MatchStatus
    ): Boolean {
        // 1. Verify match exists first to get context
        val match = matchRepository.getMatchById(matchId)
            ?: throw NotFoundException("Match not found")

        // 2. Single permission check (Fail Fast) using match's clubId
        checkUserPermissionUseCase(
            userId = userId,
            clubId = match.clubId,
            minRole = Role.COACH,
            teamId = match.teamId,
            errorMessage = "Only admins, coordinators, or team coaches can update match status"
        )

        val success = matchRepository.updateMatchStatus(matchId, status)
        if (success) {
            val score = matchEventRepository.getScoreByMatch(matchId)
            MatchSessionManager.broadcastUpdate(
                matchId,
                LiveMatchUpdate(score = score, status = status)
            )
        }
        return success
    }
}
