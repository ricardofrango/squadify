package com.ricardofrango.footballmanager.presentation.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.footballmanager.domain.usecase.CheckAndRefreshSessionUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch

class SplashViewModel(
    private val checkAndRefreshSessionUseCase: CheckAndRefreshSessionUseCase
) : ViewModel() {

    private val _navigationEvent = MutableSharedFlow<SplashNavigationEvent>()
    val navigationEvent: Flow<SplashNavigationEvent> = _navigationEvent
        .asSharedFlow()
        .onSubscription { checkSession() }

    private fun checkSession() {
        viewModelScope.launch {
            if (checkAndRefreshSessionUseCase()) {
                _navigationEvent.emit(SplashNavigationEvent.NavigateToDashboard)
            } else {
                _navigationEvent.emit(SplashNavigationEvent.NavigateToLogin)
            }
        }
    }
}
