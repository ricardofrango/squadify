package com.ricardofrango.squadify.presentation.ui.splash

sealed class SplashNavigationEvent {
    data object NavigateToLogin : SplashNavigationEvent()
    data object NavigateToDashboard : SplashNavigationEvent()
    // A single-club user goes straight to their club's details instead of Dashboard, whose clubs
    // list has nothing to offer them -- checked here rather than after Dashboard mounts so the user
    // never sees a flash of the clubs list before DashboardViewModel's own redirect would fire.
    data class NavigateToOnlyClub(val clubId: String) : SplashNavigationEvent()
}
