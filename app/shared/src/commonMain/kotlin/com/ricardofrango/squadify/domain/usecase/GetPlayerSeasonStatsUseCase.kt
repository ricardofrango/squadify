package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.model.PlayerSeasonStats

class GetPlayerSeasonStatsUseCase(
    private val playerRepository: PlayerRepository
) {
    suspend operator fun invoke(playerId: String): PlayerSeasonStats = playerRepository.getPlayerSeasonStats(playerId)
}
