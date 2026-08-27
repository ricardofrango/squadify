package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.exception.ForbiddenException
import com.ricardofrango.footballmanager.model.Player

class GetPlayerUseCase(private val playerRepository: PlayerRepository) {
    suspend operator fun invoke(playerId: String): Player {
        return playerRepository.getPlayerById(playerId) ?: throw ForbiddenException("Player not found")
    }
}
