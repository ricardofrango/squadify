package com.ricardofrango.footballmanager.presentation.ui.dashboard

import com.ricardofrango.footballmanager.model.Club

sealed class DashboardUiState {
    data class Idle(val clubs: List<Club> = emptyList(), val error: String? = null) : DashboardUiState()
    data object Loading : DashboardUiState()
}
