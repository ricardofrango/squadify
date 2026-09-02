package com.ricardofrango.footballmanager.presentation.ui.teamdetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.footballmanager.domain.usecase.GetTeamDetailsUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class TeamDetailsViewModel(
    private val getTeamDetailsUseCase: GetTeamDetailsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<TeamDetailsUiState>(TeamDetailsUiState.Idle())
    val state: StateFlow<TeamDetailsUiState> = _state.asStateFlow()

    fun loadTeamDetails(teamId: String) {
        viewModelScope.launch {
            _state.update { TeamDetailsUiState.Loading }
            try {
                val details = getTeamDetailsUseCase(teamId)
                _state.update { TeamDetailsUiState.Idle(details = details) }
            } catch (e: Exception) {
                _state.update { TeamDetailsUiState.Idle(error = e.message ?: "Failed to load team details") }
            }
        }
    }
}
