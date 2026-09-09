package com.ricardofrango.clubmanager.presentation.ui.splash

sealed class SplashNavigationEvent {
    data object NavigateToLogin : SplashNavigationEvent()
    data object NavigateToDashboard : SplashNavigationEvent()
}
