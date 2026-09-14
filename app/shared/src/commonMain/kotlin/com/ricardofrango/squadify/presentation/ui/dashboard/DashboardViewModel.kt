package com.ricardofrango.squadify.presentation.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.domain.usecase.GetClubsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val getClubsUseCase: GetClubsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<DashboardUiState>(DashboardUiState.Idle())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()
        .onSubscription {
            // Only trigger load on subscription if we are in the initial Idle state with no data
            val currentState = _state.value
            if (currentState is DashboardUiState.Idle && currentState.clubs.isEmpty()) {
                loadClubs()
            }
        }

    /** Cold start / retry-after-error: no clubs on screen yet, so the full-screen Loading state is
     * appropriate -- there's nothing underneath it to preserve. */
    fun loadClubs() {
        viewModelScope.launch {
            _state.value = DashboardUiState.Loading
            try {
                val clubs = getClubsUseCase()
                _state.value = DashboardUiState.Idle(clubs = clubs)
            } catch (e: Exception) {
                _state.value = DashboardUiState.Idle(error = e.message ?: "Failed to load clubs")
            }
        }
    }

    /** Pull-to-refresh: the club list already on screen stays visible under the small refresh
     * indicator instead of being replaced by the full-screen Loading state. */
    fun refresh() {
        val current = _state.value as? DashboardUiState.Idle ?: return
        viewModelScope.launch {
            _state.value = current.copy(isRefreshing = true, error = null)
            try {
                val clubs = getClubsUseCase()
                _state.value = DashboardUiState.Idle(clubs = clubs)
            } catch (e: Exception) {
                _state.value = current.copy(isRefreshing = false, error = e.message ?: "Failed to load clubs")
            }
        }
    }
}
