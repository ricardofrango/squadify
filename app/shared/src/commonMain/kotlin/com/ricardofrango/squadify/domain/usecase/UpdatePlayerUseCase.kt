package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.model.UpdatePlayerRequest

class UpdatePlayerUseCase(
    private val playerRepository: PlayerRepository
) {
    suspend operator fun invoke(playerId: String, request: UpdatePlayerRequest) {
        playerRepository.updatePlayer(playerId, request)
    }
}
