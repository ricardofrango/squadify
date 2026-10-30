package com.ricardofrango.squadify.coach.presentation.ui.spacesetup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.coach.domain.SetUpSpaceUseCase
import com.ricardofrango.squadify.data.remote.ApiException
import com.ricardofrango.squadify.domain.AgeGroupDefaults
import com.ricardofrango.squadify.domain.usecase.LogoutUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

class SpaceSetupViewModel(
    private val setUpSpaceUseCase: SetUpSpaceUseCase,
    private val logoutUseCase: LogoutUseCase
) : ViewModel() {
    val today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

    /** The season the server will pick (July to June), for the "Sub N" names. */
    val seasonEndYear: Int = AgeGroupDefaults.currentSeasonEndYear(today)

    private val _state = MutableStateFlow(SpaceSetupUiState())
    val state: StateFlow<SpaceSetupUiState> = _state.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<SpaceSetupNavigationEvent>()
    val navigationEvent: SharedFlow<SpaceSetupNavigationEvent> = _navigationEvent.asSharedFlow()

    fun onFormChanged(form: SpaceSetupForm) {
        _state.update { it.copy(form = form, failed = false) }
    }

    fun onCreateClicked() {
        val request = _state.value.form.toRequest(seasonEndYear) ?: return
        if (_state.value.isSubmitting) return
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, failed = false) }
            try {
                setUpSpaceUseCase(request)
                _navigationEvent.emit(SpaceSetupNavigationEvent.NavigateToHome)
            } catch (e: ApiException) {
                // 409: this account already has a space (set up on another device); open it.
                if (e.statusCode == 409) {
                    _navigationEvent.emit(SpaceSetupNavigationEvent.NavigateToHome)
                } else {
                    _state.update { it.copy(isSubmitting = false, failed = true) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isSubmitting = false, failed = true) }
            }
        }
    }

    fun onSignOutClicked() {
        viewModelScope.launch {
            logoutUseCase()
            _navigationEvent.emit(SpaceSetupNavigationEvent.NavigateToLogin)
        }
    }
}
