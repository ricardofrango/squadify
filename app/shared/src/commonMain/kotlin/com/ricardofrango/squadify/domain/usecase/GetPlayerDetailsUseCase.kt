package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.model.PlayerProfile

class GetPlayerDetailsUseCase(
    private val playerRepository: PlayerRepository
) {
    suspend operator fun invoke(playerId: String): PlayerProfile {
        return playerRepository.getPlayerById(playerId)
    }
}
