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
        clubId: String,
        matchId: String,
        playerIds: List<String>
    ): Boolean {
        val match =
            matchRepository.getMatchById(matchId) ?: throw NotFoundException("Match not found")
        checkPermission(userId, clubId, match.teamId, "Insufficient permissions to manage call-ups")

        return matchRepository.manageCallUps(matchId, playerIds)
    }

    suspend fun updateCallUpStatus(
        userId: String,
        clubId: String,
        callUpId: String,
        status: CallUpStatus,
        notes: String?
    ): Boolean {
        val callUp =
            matchRepository.getCallUpById(callUpId) ?: throw NotFoundException("Call-up not found")
        val match = matchRepository.getMatchById(callUp.matchId)
            ?: throw NotFoundException("Match not found")

        checkPermission(userId, clubId, match.teamId, "Insufficient permissions to update call-up")

        return matchRepository.updateCallUp(callUpId, status, notes)
    }

    private suspend fun checkPermission(
        userId: String,
        clubId: String,
        teamId: String,
        errorMessage: String
    ) {
        checkUserPermissionUseCase(
            userId = userId,
            clubId = clubId,
            minRole = Role.COACH,
            teamId = teamId,
            errorMessage = errorMessage
        )
    }
}
