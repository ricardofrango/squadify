package com.ricardofrango.squadify.presentation.ui.splash

import com.ricardofrango.squadify.presentation.navigation.Screen

sealed class SplashNavigationEvent {
    data object NavigateToLogin : SplashNavigationEvent()
    // The app's home once signed in (SignedInHome).
    data class NavigateToHome(val screen: Screen) : SplashNavigationEvent()
    // A shared web link (see AppNavigation's initialDeepLinkRouteOrNull) resolves here -- only once the
    // session is confirmed valid, so an expired/logged-out visitor still lands on Login like normal
    // rather than bypassing it. [route] is the raw NavController route string, navigated to verbatim.
    data class NavigateToDeepLink(val route: String) : SplashNavigationEvent()
    // A tapped push notification (see PushLinkInbox): the whole back stack to open, bottom first.
    data class NavigateToBackStack(val screens: List<Screen>) : SplashNavigationEvent()
}
