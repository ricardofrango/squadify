package com.ricardofrango.footballmanager.presentation.ui.teamdetails

import com.ricardofrango.footballmanager.model.TeamDetails

sealed class TeamDetailsUiState {
    data object Loading : TeamDetailsUiState()
    data class Idle(val details: TeamDetails) : TeamDetailsUiState()
    data class Error(val message: String) : TeamDetailsUiState()
}
