package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.MatchRepository
import com.ricardofrango.footballmanager.exception.NotFoundException
import com.ricardofrango.footballmanager.model.CallUpStatus
import com.ricardofrango.footballmanager.model.Role

class ManageCallUpUseCase(
    private val matchRepository: MatchRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase
) {
    suspend fun manageCallUps(
        userId: String,
        matchId: String,
        playerIds: List<String>
    ): Boolean {
        // 1. Fetch match first to get context
        val match =
            matchRepository.getMatchById(matchId) ?: throw NotFoundException("Match not found")

        // 2. Single permission check with context from match
        checkUserPermissionUseCase(
            userId = userId,
            clubId = match.clubId,
            minRole = Role.COACH,
            teamId = match.teamId,
            errorMessage = "Insufficient permissions to manage call-ups"
        )

        return matchRepository.manageCallUps(matchId, playerIds)
    }

    suspend fun updateCallUpStatus(
        userId: String,
        callUpId: String,
        status: CallUpStatus,
        notes: String?
    ): Boolean {
        // 1. Fetch metadata
        val callUp = matchRepository.getCallUpById(callUpId)
            ?: throw NotFoundException("Call-up not found")
        val match = matchRepository.getMatchById(callUp.matchId)
            ?: throw NotFoundException("Match not found")

        // 2. Single permission check using match metadata
        checkUserPermissionUseCase(
            userId = userId,
            clubId = match.clubId,
            minRole = Role.COACH,
            teamId = match.teamId,
            errorMessage = "Insufficient permissions to update call-up"
        )

        return matchRepository.updateCallUp(callUpId, status, notes)
    }
}
