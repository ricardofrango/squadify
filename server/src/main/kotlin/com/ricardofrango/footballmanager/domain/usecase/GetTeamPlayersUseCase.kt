package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.model.Player

class GetTeamPlayersUseCase(
    private val playerRepository: PlayerRepository
) {
    suspend operator fun invoke(teamId: String): List<Player> {
        return playerRepository.getPlayersByTeam(teamId)
    }
}
