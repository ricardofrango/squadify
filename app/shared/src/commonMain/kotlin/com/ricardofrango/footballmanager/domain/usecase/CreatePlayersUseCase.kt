package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.model.CreatePlayersRequest
import com.ricardofrango.footballmanager.model.Player

class CreatePlayersUseCase(
    private val playerRepository: PlayerRepository
) {
    suspend operator fun invoke(request: CreatePlayersRequest): List<Player> {
        return playerRepository.createPlayers(request)
    }
}
