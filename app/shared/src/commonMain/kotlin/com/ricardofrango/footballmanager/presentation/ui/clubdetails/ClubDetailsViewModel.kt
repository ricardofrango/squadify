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

    private fun applyFilters(state: ClubDetailsUiState.Idle): ClubDetailsUiState.Idle {
        val details = state.details ?: return state
        
        val filteredTeams = details.teams.filter { team ->
            (state.selectedSeasonId == null || team.seasonId == state.selectedSeasonId) &&
            (state.selectedAgeGroupId == null || team.ageGroupId == state.selectedAgeGroupId)
        }
        
        // For players, filtering logic might be more complex if they belong to multiple teams/seasons.
        // For now, we'll just filter teams and let players as is or filter by age group if applicable.
        
        return state.copy(filteredTeams = filteredTeams)
    }
}
