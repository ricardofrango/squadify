package com.ricardofrango.squadify.presentation.ui.playerdetails

import com.ricardofrango.squadify.model.PlayerProfile
import com.ricardofrango.squadify.model.PlayerSeason
import com.ricardofrango.squadify.model.PlayerSeasonStats
import com.ricardofrango.squadify.model.StatsFilter

sealed class PlayerDetailsUiState {
    data object Loading : PlayerDetailsUiState()
    data class Error(val message: String) : PlayerDetailsUiState()
    data class Idle(val profile: PlayerProfile) : PlayerDetailsUiState()
}

/**
 * The season stats card (design 30) loads separately from the profile, so a failure stays inside the
 * card and the header and Player info never move.
 */
sealed class SeasonStatsUiState {
    data object Loading : SeasonStatsUiState()
    data object Error : SeasonStatsUiState()
    data class Loaded(
        val stats: PlayerSeasonStats,
        val selectedSeasonId: String?,
        // Persists while switching seasons (design 30f).
        val filter: StatsFilter = StatsFilter.ALL
    ) : SeasonStatsUiState() {
        val selectedSeason: PlayerSeason? get() = stats.seasons.firstOrNull { it.seasonId == selectedSeasonId }
    }
}
