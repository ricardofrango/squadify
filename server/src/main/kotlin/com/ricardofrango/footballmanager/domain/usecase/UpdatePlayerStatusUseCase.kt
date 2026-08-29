package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.exception.ForbiddenException
import com.ricardofrango.footballmanager.exception.ValidationException
import com.ricardofrango.footballmanager.model.Role

class UpdatePlayerStatusUseCase(
    private val playerRepository: PlayerRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase
) {
    suspend operator fun invoke(
        userId: String,
        clubId: String,
        playerId: String,
        active: Boolean
    ): Boolean {
        // 1. Check permissions
        checkUserPermissionUseCase(
            userId = userId,
            clubId = clubId,
            minRole = Role.COORDINATOR,
            errorMessage = "Only admins or coordinators can change player status"
        )

        // 2. Verify player exists and belongs to the club
        val player = playerRepository.getPlayerById(playerId)
            ?: throw ValidationException("Player not found")

        if (player.clubId != clubId) {
            throw ForbiddenException("Player does not belong to this club")
        }

        return playerRepository.updateActiveStatus(playerId, active)
    }
}
