package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.MatchRepository
import com.ricardofrango.clubmanager.domain.service.TransactionManager
import com.ricardofrango.clubmanager.exception.NotFoundException
import com.ricardofrango.clubmanager.model.*

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
