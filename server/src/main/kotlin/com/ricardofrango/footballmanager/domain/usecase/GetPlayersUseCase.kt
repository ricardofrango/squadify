package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.model.Player

class GetPlayersUseCase(private val playerRepository: PlayerRepository) {
    suspend operator fun invoke(clubId: String, teamId: String? = null): List<Player> {
        return if (teamId != null) {
            playerRepository.getPlayersByTeam(teamId)
        } else {
            playerRepository.getPlayersByClub(clubId)
        }
    }
}
