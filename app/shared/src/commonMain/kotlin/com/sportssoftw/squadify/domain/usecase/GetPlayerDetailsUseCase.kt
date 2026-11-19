package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.PlayerRepository
import com.sportssoftw.squadify.model.PlayerProfile

class GetPlayerDetailsUseCase(
    private val playerRepository: PlayerRepository
) {
    suspend operator fun invoke(playerId: String): PlayerProfile {
        return playerRepository.getPlayerById(playerId)
    }
}
