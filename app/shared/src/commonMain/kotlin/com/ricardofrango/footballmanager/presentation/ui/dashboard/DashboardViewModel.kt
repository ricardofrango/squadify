package com.ricardofrango.footballmanager.presentation.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.footballmanager.domain.usecase.GetClubsUseCase
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
        .onSubscription { loadClubs() }

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
}
