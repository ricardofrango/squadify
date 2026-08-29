package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.MatchRepository
import com.ricardofrango.footballmanager.exception.NotFoundException
import com.ricardofrango.footballmanager.model.CallUpStatus
import com.ricardofrango.footballmanager.model.Role
import com.ricardofrango.footballmanager.model.UpdateCallUpRequest

class UpdateCallUpStatusUseCase(
    private val matchRepository: MatchRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase
) {
    suspend operator fun invoke(
        userId: String,
        callUpId: String,
        request: UpdateCallUpRequest,
    ): Boolean {
        // 1. Fetch metadata
        val callUp = matchRepository.getCallUpById(callUpId) ?: throw NotFoundException("Call-up not found")
        val match = matchRepository.getMatchById(callUp.matchId) ?: throw NotFoundException("Match not found")

        // 2. Single permission check using match metadata
        checkUserPermissionUseCase(
            userId = userId,
            clubId = match.clubId,
            minRole = Role.COACH,
            teamId = match.teamId,
            errorMessage = "Insufficient permissions to update call-up"
        )

        return matchRepository.updateCallUp(callUpId, request.status, request.notes)
    }
}
