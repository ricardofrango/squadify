package com.ricardofrango.squadify.presentation.ui.teamdetails

import com.ricardofrango.squadify.model.TeamDetails

sealed class TeamDetailsUiState {
    data object Loading : TeamDetailsUiState()
    data class Idle(val details: TeamDetails) : TeamDetailsUiState()
    data class Error(val message: String) : TeamDetailsUiState()
}
