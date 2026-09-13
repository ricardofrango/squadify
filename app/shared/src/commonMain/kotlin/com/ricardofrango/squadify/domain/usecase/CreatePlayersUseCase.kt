package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.model.CreatePlayersRequest
import com.ricardofrango.squadify.model.Player

class CreatePlayersUseCase(
    private val playerRepository: PlayerRepository
) {
    suspend operator fun invoke(request: CreatePlayersRequest): List<Player> {
        return playerRepository.createPlayers(request)
    }
}
