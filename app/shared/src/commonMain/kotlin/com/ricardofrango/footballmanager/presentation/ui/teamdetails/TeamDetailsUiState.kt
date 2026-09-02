package com.ricardofrango.footballmanager.presentation.ui.teamdetails

import com.ricardofrango.footballmanager.model.TeamDetails

sealed class TeamDetailsUiState {
    data class Idle(val details: TeamDetails? = null, val error: String? = null) : TeamDetailsUiState()
    data object Loading : TeamDetailsUiState()
}
