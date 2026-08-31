package com.ricardofrango.footballmanager.presentation.ui.splash

sealed class SplashNavigationEvent {
    data object NavigateToLogin : SplashNavigationEvent()
    data object NavigateToDashboard : SplashNavigationEvent()
}
