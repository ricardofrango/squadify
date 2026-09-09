package com.ricardofrango.clubmanager.presentation.ui.dashboard

import com.ricardofrango.clubmanager.model.Club

sealed class DashboardUiState {
    data class Idle(val clubs: List<Club> = emptyList(), val error: String? = null) : DashboardUiState()
    data object Loading : DashboardUiState()
}
