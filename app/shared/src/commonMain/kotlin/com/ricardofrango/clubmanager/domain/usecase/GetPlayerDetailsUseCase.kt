package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.PlayerRepository
import com.ricardofrango.clubmanager.model.Player

class GetPlayerDetailsUseCase(
    private val playerRepository: PlayerRepository
) {
    suspend operator fun invoke(playerId: String): Player {
        return playerRepository.getPlayerById(playerId)
    }
}
