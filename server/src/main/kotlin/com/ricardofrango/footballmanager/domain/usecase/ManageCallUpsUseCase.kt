package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.MatchRepository
import com.ricardofrango.footballmanager.domain.service.TransactionManager
import com.ricardofrango.footballmanager.exception.NotFoundException
import com.ricardofrango.footballmanager.model.*

class ManageCallUpsUseCase(
    private val matchRepository: MatchRepository,
    private val transactionManager: TransactionManager,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase
) {
    suspend operator fun invoke(
        userId: String,
        matchId: String,
        request: ManageCallUpRequest
    ): Boolean = transactionManager.runInTransaction {
        // 1. Fetch match first to get context (required for granular check)
        val match = matchRepository.getMatchById(matchId) ?: throw NotFoundException("Match not found")

        // 2. Single permission check with full context from match
        checkUserPermissionUseCase(
            userId = userId,
            clubId = match.clubId,
            minTeamRole = TeamRole.COACH,
            teamId = match.teamId,
            errorMessage = "Insufficient permissions to manage call-ups"
        )

        matchRepository.manageCallUps(matchId, request.playerIds)
    }
}
