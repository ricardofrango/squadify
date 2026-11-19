package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.PlayerRepository
import com.sportssoftw.squadify.model.PlayerSeasonStats

class GetPlayerSeasonStatsUseCase(
    private val playerRepository: PlayerRepository
) {
    suspend operator fun invoke(playerId: String): PlayerSeasonStats = playerRepository.getPlayerSeasonStats(playerId)
}
