package com.ricardofrango.squadify.coach.presentation.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.coach.domain.GetSpaceUseCase
import com.ricardofrango.squadify.domain.usecase.GetAgeGroupsUseCase
import com.ricardofrango.squadify.domain.usecase.LogoutUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CoachHomeViewModel(
    private val getSpaceUseCase: GetSpaceUseCase,
    private val getAgeGroupsUseCase: GetAgeGroupsUseCase,
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
            try {
                val space = getSpaceUseCase()
                if (space == null) {
                    _navigationEvent.emit(CoachHomeNavigationEvent.NavigateToSetup)
                    return@launch
                }
                // The space is a club row on the server, so its age groups are asked for by its id,
                // youngest first.
                val ageGroups = getAgeGroupsUseCase(space.space.id).sortedByDescending { it.endYear }
                _state.value = CoachHomeUiState.Loaded(space, ageGroups)
            } catch (e: Exception) {
                _state.value = CoachHomeUiState.Failed
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
