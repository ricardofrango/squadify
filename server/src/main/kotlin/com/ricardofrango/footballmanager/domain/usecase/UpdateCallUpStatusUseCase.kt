package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.MatchRepository
import com.ricardofrango.footballmanager.domain.service.TransactionManager
import com.ricardofrango.footballmanager.exception.NotFoundException
import com.ricardofrango.footballmanager.model.*

class UpdateCallUpStatusUseCase(
    private val matchRepository: MatchRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(
        userId: String,
        callUpId: String,
        request: UpdateCallUpRequest,
    ): Boolean = transactionManager.runInTransaction {
        // 1. Fetch metadata
        val callUp = matchRepository.getCallUpById(callUpId) ?: throw NotFoundException("Call-up not found")
        val match = matchRepository.getMatchById(callUp.matchId) ?: throw NotFoundException("Match not found")

        // 2. Single permission check using match metadata
        checkUserPermissionUseCase(
            userId = userId,
            clubId = match.clubId,
            minTeamRole = TeamRole.COACH,
            teamId = match.teamId,
            errorMessage = "Insufficient permissions to update call-up"
        )

        matchRepository.updateCallUp(callUpId, request.status, request.notes)
    }
}
