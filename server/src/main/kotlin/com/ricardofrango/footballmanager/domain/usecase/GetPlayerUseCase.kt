package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.exception.NotFoundException
import com.ricardofrango.footballmanager.model.Player
import com.ricardofrango.footballmanager.model.Role

class GetPlayerUseCase(
    private val playerRepository: PlayerRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase
) {
    suspend operator fun invoke(userId: String, playerId: String): Player {
        val player = playerRepository.getPlayerById(playerId) ?: throw NotFoundException("Player not found")

        // 1. Check permissions first (Fail fast)
        checkUserPermissionUseCase(userId, player.clubId, Role.PLAYER)
        
        return player
    }
}
