package com.ricardofrango.footballmanager.presentation.ui.clubdetails

import com.ricardofrango.footballmanager.domain.usecase.ClubDetails
import com.ricardofrango.footballmanager.model.Team
import com.ricardofrango.footballmanager.model.Player
import com.ricardofrango.footballmanager.model.AgeGroup
import com.ricardofrango.footballmanager.model.ClubHome

sealed class ClubDetailsUiState {
    data class Idle(
        val details: ClubDetails? = null,
        // The Home tab's aggregate. Loaded alongside `details`; null while loading or if it failed
        // (the Home tab then shows its own light empty/error state).
        val home: ClubHome? = null,
        val filteredTeams: List<Team> = emptyList(),
        val filteredPlayers: List<Player> = emptyList(),
        // Tab order: 0 Home, 1 Teams, 2 Players, 3 Age Groups. Home is the default.
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
        // Age Groups tab filter -- season only, and unlike the other tabs there's no "All seasons":
        // an age group only means something within one season. Defaults to the most recent season
        // (by end date) when details load.
        val selectedAgeGroupsSeasonId: String? = null,
        val error: String? = null,
        // Resolved once when details load, so the screen just reads a flag instead of re-deriving it
        // from `details` on every recomposition: everything on this screen (teams, players, filters)
        // is season-scoped, so a club with no seasons has nothing meaningful to show.
        val showNoSeasonsState: Boolean = false,
        // Whether the "no seasons" state should offer a create-season action -- only a CLUB_ADMIN,
        // COORDINATOR, or platform admin can create one (see CreateSeasonUseCase / PROCESS_FLOWS.md §3.3).
        val canEditClub: Boolean = false,
        // Same idea, one level down: a club can have seasons but no age groups yet, which leaves teams
        // and players just as unorganisable. Only checked once there's at least one season -- see
        // ClubDetailsViewModel.
        val showNoAgeGroupsState: Boolean = false,
    ) : ClubDetailsUiState()
    data object Loading : ClubDetailsUiState()
}
