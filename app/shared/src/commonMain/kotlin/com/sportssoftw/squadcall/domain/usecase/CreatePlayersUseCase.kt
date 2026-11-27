package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.PlayerRepository
import com.sportssoftw.squadcall.model.CreatePlayersRequest
import com.sportssoftw.squadcall.model.Player

class CreatePlayersUseCase(
    private val playerRepository: PlayerRepository
) {
    suspend operator fun invoke(request: CreatePlayersRequest): List<Player> {
        return playerRepository.createPlayers(request)
    }
}
