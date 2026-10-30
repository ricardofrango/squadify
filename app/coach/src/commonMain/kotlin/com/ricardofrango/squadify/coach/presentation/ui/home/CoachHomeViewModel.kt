package com.ricardofrango.squadify.coach.presentation.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.coach.domain.GetSpacesUseCase
import com.ricardofrango.squadify.domain.usecase.LogoutUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CoachHomeViewModel(
    private val getSpacesUseCase: GetSpacesUseCase,
    private val logoutUseCase: LogoutUseCase
) : ViewModel() {
    private val _state = MutableStateFlow<CoachHomeUiState>(CoachHomeUiState.Loading)
    val state: StateFlow<CoachHomeUiState> = _state.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<CoachHomeNavigationEvent>()
    val navigationEvent: SharedFlow<CoachHomeNavigationEvent> = _navigationEvent.asSharedFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = CoachHomeUiState.Loading
            _state.value = try {
                CoachHomeUiState.Loaded.of(getSpacesUseCase())
            } catch (e: Exception) {
                CoachHomeUiState.Failed
            }
        }
    }

    fun onSignOutClicked() {
        viewModelScope.launch {
            logoutUseCase()
            _navigationEvent.emit(CoachHomeNavigationEvent.NavigateToLogin)
        }
    }
}
