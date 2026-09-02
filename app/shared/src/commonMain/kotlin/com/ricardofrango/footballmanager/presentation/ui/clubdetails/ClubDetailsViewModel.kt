package com.ricardofrango.footballmanager.presentation.ui.clubdetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.footballmanager.domain.usecase.GetClubDetailsUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ClubDetailsViewModel(
    private val getClubDetailsUseCase: GetClubDetailsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<ClubDetailsUiState>(ClubDetailsUiState.Idle())
    val state: StateFlow<ClubDetailsUiState> = _state.asStateFlow()

    fun loadClubDetails(clubId: String) {
        viewModelScope.launch {
            _state.update { ClubDetailsUiState.Loading }
            try {
                val details = getClubDetailsUseCase(clubId)
                _state.update { 
                    ClubDetailsUiState.Idle(
                        details = details,
                        filteredTeams = details.teams,
                        filteredPlayers = details.players
                    )
                }
            } catch (e: Exception) {
                _state.update { ClubDetailsUiState.Idle(error = e.message ?: "Failed to load club details") }
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

    fun onPlayerTeamSelected(teamId: String?) {
        _state.update { state ->
            if (state is ClubDetailsUiState.Idle) {
                val newState = state.copy(selectedPlayerTeamId = teamId)
                applyFilters(newState)
            } else state
        }
    }

    private fun applyFilters(state: ClubDetailsUiState.Idle): ClubDetailsUiState.Idle {
        val details = state.details ?: return state
        
        // Filter Teams
        val filteredTeams = details.teams.filter { team ->
            (state.selectedSeasonId == null || team.seasonId == state.selectedSeasonId) &&
            (state.selectedAgeGroupId == null || team.ageGroupId == state.selectedAgeGroupId)
        }
        
        // Filter Players
        val filteredPlayers = details.players.filter { player ->
            // Get all team associations for this player
            val playerTeamIds = details.teamPlayers
                .filter { it.playerId == player.id }
                .map { it.teamId }
            
            val matchesTeam = state.selectedPlayerTeamId == null || playerTeamIds.contains(state.selectedPlayerTeamId)
            
            val matchesSeason = if (state.selectedPlayerSeasonId == null) {
                true
            } else {
                // Get teams for the selected season
                val teamsInSeasonIds = details.teams
                    .filter { it.seasonId == state.selectedPlayerSeasonId }
                    .map { it.id }
                // Check if player belongs to any team in that season
                playerTeamIds.any { teamsInSeasonIds.contains(it) }
            }
            
            matchesTeam && matchesSeason
        }
        
        return state.copy(
            filteredTeams = filteredTeams,
            filteredPlayers = filteredPlayers
        )
    }
}
