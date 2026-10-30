package com.ricardofrango.squadify.presentation.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.domain.usecase.CheckAndRefreshSessionUseCase
import com.ricardofrango.squadify.domain.usecase.RegisterCurrentFcmTokenUseCase
import com.ricardofrango.squadify.presentation.navigation.PushLinkInbox
import com.ricardofrango.squadify.presentation.navigation.Screen
import com.ricardofrango.squadify.presentation.navigation.SignedInHome
import com.ricardofrango.squadify.presentation.navigation.pushLinkBackStack
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch

class SplashViewModel(
    private val checkAndRefreshSessionUseCase: CheckAndRefreshSessionUseCase,
    private val signedInHome: SignedInHome,
    private val registerCurrentFcmTokenUseCase: RegisterCurrentFcmTokenUseCase,
    private val pushLinkInbox: PushLinkInbox
) : ViewModel() {

    // Set once, before the first collection of [navigationEvent] -- see SplashScreen. Not a constructor
    // param: Koin's default viewModel factory has no way to pass a per-navigation value into it, and this
    // is only ever known at the call site (AppNavigation reads the URL once, synchronously, before this
    // ViewModel is even created).
    private var deepLinkRoute: String? = null

    fun setDeepLinkRoute(route: String?) {
        deepLinkRoute = route
    }

    private val _navigationEvent = MutableSharedFlow<SplashNavigationEvent>()
    val navigationEvent: Flow<SplashNavigationEvent> = _navigationEvent
        .asSharedFlow()
        .onSubscription { checkSession() }

    private fun checkSession() {
        viewModelScope.launch {
            if (checkAndRefreshSessionUseCase()) {
                registerCurrentFcmTokenUseCase()

                // A deep-linked route (a shared web link) takes priority over the home
                // below, but only now that the session is confirmed
                // valid -- an expired/logged-out visitor never reaches this branch at all, so a stale
                // shared link can't be used to bypass Login.
                val route = deepLinkRoute
                if (route != null) {
                    _navigationEvent.emit(SplashNavigationEvent.NavigateToDeepLink(route))
                    return@launch
                }

                // The app's own home (SignedInHome), checked up front so it opens straight away. In
                // the clubs app a single-club session lands directly on that club instead of the club
                // list, which would only redirect there itself after a visible flash. The clubs app's
                // check also keeps the clubs' colours, for a screen opened from a push notification.
                val home = signedInHome.screen()
                // A tapped push notification opens its club and target instead (§56). Taken only here,
                // with the session confirmed; signed out, it waits in the inbox until after Login.
                val pushLink = pushLinkInbox.take()
                if (pushLink != null) {
                    val onlyClubId = (home as? Screen.ClubDetails)?.takeIf { it.isRootClub }?.clubId
                    _navigationEvent.emit(SplashNavigationEvent.NavigateToBackStack(pushLinkBackStack(pushLink, onlyClubId)))
                    return@launch
                }
                _navigationEvent.emit(SplashNavigationEvent.NavigateToHome(home))
            } else {
                _navigationEvent.emit(SplashNavigationEvent.NavigateToLogin)
            }
        }
    }
}
