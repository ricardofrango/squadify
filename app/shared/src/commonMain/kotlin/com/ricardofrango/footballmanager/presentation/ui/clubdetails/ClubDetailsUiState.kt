package com.ricardofrango.footballmanager.presentation.ui.clubdetails

import com.ricardofrango.footballmanager.domain.usecase.ClubDetails
import com.ricardofrango.footballmanager.model.Team
import com.ricardofrango.footballmanager.model.Player

sealed class ClubDetailsUiState {
    data class Idle(
        val details: ClubDetails? = null,
        val filteredTeams: List<Team> = emptyList(),
        val filteredPlayers: List<Player> = emptyList(),
        // Team Filters
        val selectedSeasonId: String? = null,
        val selectedAgeGroupId: String? = null,
        // Player Filters
        val selectedPlayerSeasonId: String? = null,
        val selectedPlayerTeamId: String? = null,
        val error: String? = null
    ) : ClubDetailsUiState()
    data object Loading : ClubDetailsUiState()
}
