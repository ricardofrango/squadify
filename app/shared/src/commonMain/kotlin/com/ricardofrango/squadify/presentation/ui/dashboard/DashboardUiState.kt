package com.ricardofrango.squadify.presentation.ui.dashboard

import com.ricardofrango.squadify.model.Club

sealed class DashboardUiState {
    data object Loading : DashboardUiState()

    // A cold-start (or retry-after-error) failure: there's no club list to show underneath, so this
    // is its own blocking state rather than an Idle with an error field -- Idle is reserved for when
    // there's real idle content on screen (see Idle.error below for the one legitimate exception).
    data class Error(val message: String) : DashboardUiState()

    data class Idle(
        val clubs: List<Club> = emptyList(),
        // True only while a pull-to-refresh reload is in flight -- the club list already on screen
        // stays visible underneath the small refresh indicator instead of being replaced by the
        // full-screen Loading state, which is reserved for the very first (cold-start) load.
        val isRefreshing: Boolean = false,
        // Set only when a pull-to-refresh fails while clubs are already on screen: unlike the
        // cold-start failure above, there IS idle content here (the existing list), so this behaves
        // like Login's inline error -- shown alongside content, not instead of it.
        val error: String? = null
    ) : DashboardUiState()
}
