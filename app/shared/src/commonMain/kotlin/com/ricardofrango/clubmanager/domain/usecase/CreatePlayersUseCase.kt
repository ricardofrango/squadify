package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.PlayerRepository
import com.ricardofrango.clubmanager.model.CreatePlayersRequest
import com.ricardofrango.clubmanager.model.Player

class CreatePlayersUseCase(
    private val playerRepository: PlayerRepository
) {
    suspend operator fun invoke(request: CreatePlayersRequest): List<Player> {
        return playerRepository.createPlayers(request)
    }
}
