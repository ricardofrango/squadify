package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.PlayerRepository
import com.sportssoftw.squadcall.model.UpdatePlayerRequest

class UpdatePlayerUseCase(
    private val playerRepository: PlayerRepository
) {
    suspend operator fun invoke(playerId: String, request: UpdatePlayerRequest) {
        playerRepository.updatePlayer(playerId, request)
    }
}
