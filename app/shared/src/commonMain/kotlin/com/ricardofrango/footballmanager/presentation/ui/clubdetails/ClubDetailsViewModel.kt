package com.ricardofrango.footballmanager.presentation.ui.clubdetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.footballmanager.domain.usecase.GetClubDetailsUseCase
import com.ricardofrango.footballmanager.model.AgeGroup
import com.ricardofrango.footballmanager.model.Team
import footballmanager.app.shared.generated.resources.Res
import footballmanager.app.shared.generated.resources.msg_failed_to_load_club_details
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

class ClubDetailsViewModel(
    private val getClubDetailsUseCase: GetClubDetailsUseCase
) : ViewModel() {

    // Starts in Loading, not Idle(): loadClubDetails() always runs immediately on mount (from the
    // screen's LaunchedEffect), so an initial Idle() only bought a one-frame gap where none of the
    // screen's `when` branches match (error/showNoSeasonsState/details are all null/false) and nothing
    // renders -- during a nav transition that let the previous screen (e.g. Dashboard's pull-to-refresh
    // spinner) stay visible instead of this screen's own full-screen loading indicator.
    private val _state = MutableStateFlow<ClubDetailsUiState>(ClubDetailsUiState.Loading)
    val state: StateFlow<ClubDetailsUiState> = _state.asStateFlow()

    fun loadClubDetails(clubId: String, forceReload: Boolean = false) {
        val currentState = _state.value

        // If we already have details for this club, don't trigger a full reload/flicker -- unless the
        // caller knows the data is stale (e.g. just returned from creating a season) and explicitly
        // asks to bypass this.
        if (!forceReload && currentState is ClubDetailsUiState.Idle && currentState.details?.club?.id == clubId) {
            return
        }

        // Capture current tab to preserve it across loading states
        val currentTab = (currentState as? ClubDetailsUiState.Idle)?.selectedTab ?: 0

        viewModelScope.launch {
            _state.update { ClubDetailsUiState.Loading }
            try {
                val details = getClubDetailsUseCase(clubId)
                _state.update {
                    ClubDetailsUiState.Idle(
                        details = details,
                        filteredTeams = details.teams,
                        filteredPlayers = details.players,
                        selectedTab = currentTab, // Restore the captured tab
                        showNoSeasonsState = details.seasons.isEmpty(),
                        canEditClub = details.canEditClub,
                        // Only meaningful once there's at least one season to attach age groups to --
                        // an empty club (no seasons either) is already covered by showNoSeasonsState.
                        showNoAgeGroupsState = details.seasons.isNotEmpty() && details.ageGroups.isEmpty(),
                        selectedAgeGroupsSeasonId = details.seasons.maxByOrNull { it.endDate }?.id,
                    )
                }
            } catch (e: Exception) {
                // Never surface e.message to the UI: it's an implementation detail (can leak server
                // internals, exception class names, etc.), not user-facing copy. See AI_GUIDELINES.md
                // "No Raw Exception Messages in UI".
                val message = getString(Res.string.msg_failed_to_load_club_details)
                _state.update { ClubDetailsUiState.Idle(error = message) }
            }
        }
    }

    fun onSeasonSelected(seasonId: String?) {
        _state.update { state ->
            if (state is ClubDetailsUiState.Idle) {
                val newState = state.copy(selectedSeasonId = seasonId)
                applyFilters(newState)
            } else state
        }
    }

    fun onAgeGroupSelected(ageGroupId: String?) {
        _state.update { state ->
            if (state is ClubDetailsUiState.Idle) {
                val newState = state.copy(selectedAgeGroupId = ageGroupId)
                applyFilters(newState)
            } else state
        }
    }

    fun onPlayerSeasonSelected(seasonId: String?) {
        _state.update { state ->
            if (state is ClubDetailsUiState.Idle) {
                val newState = state.copy(selectedPlayerSeasonId = seasonId)
                applyFilters(newState)
            } else state
        }
    }

    fun onPlayerAgeGroupSelected(ageGroupId: String?) {
        _state.update { state ->
            if (state is ClubDetailsUiState.Idle) {
                val newState = state.copy(selectedPlayerAgeGroupId = ageGroupId)
                applyFilters(newState)
            } else state
        }
    }

    fun onAgeGroupsSeasonSelected(seasonId: String) {
        _state.update { state ->
            if (state is ClubDetailsUiState.Idle) state.copy(selectedAgeGroupsSeasonId = seasonId) else state
        }
    }

    fun onTabSelected(index: Int) {
        _state.update { state ->
            if (state is ClubDetailsUiState.Idle) {
                state.copy(selectedTab = index)
            } else state
        }
    }

    private fun applyFilters(state: ClubDetailsUiState.Idle): ClubDetailsUiState.Idle {
        val details = state.details ?: return state
        
        // Filter Teams List based on direct relations
        val filteredTeams = details.teams.filter { team ->
            (state.selectedSeasonId == null || team.seasonId == state.selectedSeasonId) &&
            (state.selectedAgeGroupId == null || team.ageGroupId == state.selectedAgeGroupId)
        }
        
        // Filter Players List by age-group membership (players belong to age-group pools, not teams).
        val filteredPlayers = details.players.filter { player ->
            val playerAgeGroupIds = details.ageGroupPlayers
                .filter { it.playerId == player.id }
                .map { it.ageGroupId }
                .toSet()

            val matchesAgeGroup = state.selectedPlayerAgeGroupId == null ||
                playerAgeGroupIds.contains(state.selectedPlayerAgeGroupId)

            val matchesSeason = if (state.selectedPlayerSeasonId == null) true else {
                details.ageGroups.any {
                    it.id in playerAgeGroupIds && it.seasonId == state.selectedPlayerSeasonId
                }
            }

            matchesSeason && matchesAgeGroup
        }
        
        return state.copy(
            filteredTeams = filteredTeams,
            filteredPlayers = filteredPlayers
        )
    }

    // Helper functions to get contextual options for the UI
    fun getAvailableAgeGroupsForTeams(state: ClubDetailsUiState.Idle): List<AgeGroup> {
        return state.details?.ageGroups?.filter { 
            state.selectedSeasonId == null || it.seasonId == state.selectedSeasonId 
        } ?: emptyList()
    }

    fun getAvailableTeamsForTeams(state: ClubDetailsUiState.Idle): List<Team> {
        return state.details?.teams?.filter { 
            (state.selectedSeasonId == null || it.seasonId == state.selectedSeasonId) &&
            (state.selectedAgeGroupId == null || it.ageGroupId == state.selectedAgeGroupId)
        } ?: emptyList()
    }

    fun getAvailableAgeGroupsForPlayers(state: ClubDetailsUiState.Idle): List<AgeGroup> {
        return state.details?.ageGroups?.filter { 
            state.selectedPlayerSeasonId == null || it.seasonId == state.selectedPlayerSeasonId 
        } ?: emptyList()
    }

    /** Age groups for the season selected on the Age Groups tab, youngest (highest birth year) first. */
    fun getAgeGroupsForAgeGroupsTab(state: ClubDetailsUiState.Idle): List<AgeGroup> {
        val seasonId = state.selectedAgeGroupsSeasonId ?: state.details?.seasons?.firstOrNull()?.id
        return state.details?.ageGroups
            ?.filter { it.seasonId == seasonId }
            ?.sortedByDescending { it.birthYear }
            ?: emptyList()
    }
}
