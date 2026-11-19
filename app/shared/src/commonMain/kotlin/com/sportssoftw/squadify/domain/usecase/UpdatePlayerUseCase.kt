package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.PlayerRepository
import com.sportssoftw.squadify.model.UpdatePlayerRequest

class UpdatePlayerUseCase(
    private val playerRepository: PlayerRepository
) {
    suspend operator fun invoke(playerId: String, request: UpdatePlayerRequest) {
        playerRepository.updatePlayer(playerId, request)
    }
}
