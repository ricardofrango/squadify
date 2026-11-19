package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.PlayerRepository
import com.sportssoftw.squadify.model.CreatePlayersRequest
import com.sportssoftw.squadify.model.Player

class CreatePlayersUseCase(
    private val playerRepository: PlayerRepository
) {
    suspend operator fun invoke(request: CreatePlayersRequest): List<Player> {
        return playerRepository.createPlayers(request)
    }
}
