package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.PlayerRepository
import com.sportssoftw.squadcall.model.PlayerSeasonStats

class GetPlayerSeasonStatsUseCase(
    private val playerRepository: PlayerRepository
) {
    suspend operator fun invoke(playerId: String): PlayerSeasonStats = playerRepository.getPlayerSeasonStats(playerId)
}
