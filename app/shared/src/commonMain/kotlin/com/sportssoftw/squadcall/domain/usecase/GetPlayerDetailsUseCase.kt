package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.PlayerRepository
import com.sportssoftw.squadcall.model.PlayerProfile

class GetPlayerDetailsUseCase(
    private val playerRepository: PlayerRepository
) {
    suspend operator fun invoke(playerId: String): PlayerProfile {
        return playerRepository.getPlayerById(playerId)
    }
}
