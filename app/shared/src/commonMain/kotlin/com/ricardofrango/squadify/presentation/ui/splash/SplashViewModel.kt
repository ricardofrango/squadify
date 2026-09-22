package com.ricardofrango.squadify.presentation.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.domain.usecase.CheckAndRefreshSessionUseCase
import com.ricardofrango.squadify.domain.usecase.GetClubsUseCase
import com.ricardofrango.squadify.domain.usecase.RegisterCurrentFcmTokenUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch

class SplashViewModel(
    private val checkAndRefreshSessionUseCase: CheckAndRefreshSessionUseCase,
    private val getClubsUseCase: GetClubsUseCase,
    private val registerCurrentFcmTokenUseCase: RegisterCurrentFcmTokenUseCase
) : ViewModel() {

    private val _navigationEvent = MutableSharedFlow<SplashNavigationEvent>()
    val navigationEvent: Flow<SplashNavigationEvent> = _navigationEvent
        .asSharedFlow()
        .onSubscription { checkSession() }

    private fun checkSession() {
        viewModelScope.launch {
            if (checkAndRefreshSessionUseCase()) {
                registerCurrentFcmTokenUseCase()
                // Checked up front so a single-club session lands directly on that club's details
                // instead of visiting Dashboard first -- Dashboard would make the exact same redirect
                // itself (DashboardViewModel.NavigateToOnlyClub, kept as a fallback), but only after
                // mounting, which is visible as a brief flash of the clubs list. A fetch failure here
                // just falls back to Dashboard, which has its own retry-capable error state.
                val onlyClub = try {
                    getClubsUseCase().singleOrNull()
                } catch (e: Exception) {
                    null
                }
                _navigationEvent.emit(
                    if (onlyClub != null) {
                        SplashNavigationEvent.NavigateToOnlyClub(onlyClub.id)
                    } else {
                        SplashNavigationEvent.NavigateToDashboard
                    }
                )
            } else {
                _navigationEvent.emit(SplashNavigationEvent.NavigateToLogin)
            }
        }
    }
}
