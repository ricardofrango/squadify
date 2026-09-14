package com.ricardofrango.squadify.presentation.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.domain.usecase.GetClubsUseCase
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.msg_failed_to_load_clubs
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

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

    private val _navigationEvent = MutableSharedFlow<DashboardNavigationEvent>()
    val navigationEvent: SharedFlow<DashboardNavigationEvent> = _navigationEvent.asSharedFlow()

    /** Cold start / retry-after-error: no clubs on screen yet, so a failure is its own blocking
     * Error state -- there's no idle content underneath it to preserve. */
    fun loadClubs() {
        viewModelScope.launch {
            _state.value = DashboardUiState.Loading
            try {
                val clubs = getClubsUseCase()
                _state.value = DashboardUiState.Idle(clubs = clubs)
                val onlyClub = clubs.singleOrNull()
                if (onlyClub != null) {
                    _navigationEvent.emit(DashboardNavigationEvent.NavigateToOnlyClub(onlyClub.id))
                }
            } catch (e: Exception) {
                _state.value = DashboardUiState.Error(getString(Res.string.msg_failed_to_load_clubs))
            }
        }
    }

    /** Pull-to-refresh: the club list already on screen stays visible under the small refresh
     * indicator instead of being replaced by the full-screen Loading/Error state; a failure here
     * surfaces inline (Idle.error) alongside the still-visible list, same idea as Login's inline error. */
    fun refresh() {
        val current = _state.value as? DashboardUiState.Idle ?: return
        viewModelScope.launch {
            _state.value = current.copy(isRefreshing = true, error = null)
            try {
                val clubs = getClubsUseCase()
                _state.value = DashboardUiState.Idle(clubs = clubs)
            } catch (e: Exception) {
                _state.value = current.copy(isRefreshing = false, error = getString(Res.string.msg_failed_to_load_clubs))
            }
        }
    }
}
