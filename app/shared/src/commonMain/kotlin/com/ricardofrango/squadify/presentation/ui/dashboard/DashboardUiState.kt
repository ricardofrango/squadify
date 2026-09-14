package com.ricardofrango.squadify.presentation.ui.dashboard

import com.ricardofrango.squadify.model.Club

sealed class DashboardUiState {
    data class Idle(
        val clubs: List<Club> = emptyList(),
        val error: String? = null,
        // True only while a pull-to-refresh reload is in flight -- the club list already on screen
        // stays visible underneath the small refresh indicator instead of being replaced by the
        // full-screen Loading state, which is reserved for the very first (cold-start) load.
        val isRefreshing: Boolean = false
    ) : DashboardUiState()
    data object Loading : DashboardUiState()
}
