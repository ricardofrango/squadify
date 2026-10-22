package com.ricardofrango.squadify.presentation.ui.splash

import com.ricardofrango.squadify.presentation.navigation.Screen

sealed class SplashNavigationEvent {
    data object NavigateToLogin : SplashNavigationEvent()
    data object NavigateToDashboard : SplashNavigationEvent()
    // A single-club user goes straight to their club's details instead of Dashboard, whose clubs
    // list has nothing to offer them -- checked here rather than after Dashboard mounts so the user
    // never sees a flash of the clubs list before DashboardViewModel's own redirect would fire.
    data class NavigateToOnlyClub(val clubId: String) : SplashNavigationEvent()
    // A shared web link (see AppNavigation's initialDeepLinkRouteOrNull) resolves here -- only once the
    // session is confirmed valid, so an expired/logged-out visitor still lands on Login like normal
    // rather than bypassing it. [route] is the raw NavController route string, navigated to verbatim.
    data class NavigateToDeepLink(val route: String) : SplashNavigationEvent()
    // A tapped push notification (see PushLinkInbox): the whole back stack to open, bottom first.
    data class NavigateToBackStack(val screens: List<Screen>) : SplashNavigationEvent()
}
