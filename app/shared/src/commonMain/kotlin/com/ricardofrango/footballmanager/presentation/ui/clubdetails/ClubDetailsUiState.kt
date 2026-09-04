package com.ricardofrango.footballmanager.presentation.ui.clubdetails

import com.ricardofrango.footballmanager.domain.usecase.ClubDetails
import com.ricardofrango.footballmanager.model.Team
import com.ricardofrango.footballmanager.model.Player
import com.ricardofrango.footballmanager.model.AgeGroup

sealed class ClubDetailsUiState {
    data class Idle(
        val details: ClubDetails? = null,
        val filteredTeams: List<Team> = emptyList(),
        val filteredPlayers: List<Player> = emptyList(),
        val selectedTab: Int = 0,
        // Available Filter Options (Contextual)
        val availableAgeGroups: List<AgeGroup> = emptyList(),
        val availableTeams: List<Team> = emptyList(),
        // Team Filters
        val selectedSeasonId: String? = null,
        val selectedAgeGroupId: String? = null,
        // Player Filters
        val selectedPlayerSeasonId: String? = null,
        val selectedPlayerAgeGroupId: String? = null,
        val selectedPlayerTeamId: String? = null,
        val error: String? = null,
        // Resolved once when details load, so the screen just reads a flag instead of re-deriving it
        // from `details` on every recomposition: everything on this screen (teams, players, filters)
        // is season-scoped, so a club with no seasons has nothing meaningful to show.
        val showNoSeasonsState: Boolean = false,
        // Whether the "no seasons" state should offer a create-season action -- only a CLUB_ADMIN,
        // COORDINATOR, or platform admin can create one (see CreateSeasonUseCase / PROCESS_FLOWS.md §3.3).
        val canCreateSeason: Boolean = false
    ) : ClubDetailsUiState()
    data object Loading : ClubDetailsUiState()
}
