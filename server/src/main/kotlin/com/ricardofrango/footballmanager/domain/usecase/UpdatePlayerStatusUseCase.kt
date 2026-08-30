package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.exception.ValidationException
import com.ricardofrango.footballmanager.model.ClubRole

class UpdatePlayerStatusUseCase(
    private val playerRepository: PlayerRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase
) {
    suspend operator fun invoke(
        userId: String,
        playerId: String,
        active: Boolean
    ): Boolean {
        // 1. Verify player exists first to get context
        val player = playerRepository.getPlayerById(playerId)
            ?: throw ValidationException("Player not found")

        // 2. Check permissions using player's clubId
        checkUserPermissionUseCase(
            userId = userId,
            clubId = player.clubId,
            minClubRole = ClubRole.COORDINATOR,
            errorMessage = "Only admins or coordinators can change player status"
        )

        return playerRepository.updateActiveStatus(playerId, active)
    }
}
