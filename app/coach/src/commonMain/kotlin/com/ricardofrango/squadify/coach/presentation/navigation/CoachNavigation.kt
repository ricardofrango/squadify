package com.ricardofrango.squadify.coach.presentation.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.ricardofrango.squadify.coach.presentation.ui.home.CoachHomeScreen
import com.ricardofrango.squadify.coach.presentation.ui.spacesetup.SpaceSetupScreen
import com.ricardofrango.squadify.domain.service.SessionEventBus
import com.ricardofrango.squadify.presentation.navigation.AppNavigator
import com.ricardofrango.squadify.presentation.navigation.SPLASH_ENTRY
import com.ricardofrango.squadify.presentation.navigation.Screen
import com.ricardofrango.squadify.presentation.navigation.ScreenRegistry
import com.ricardofrango.squadify.presentation.navigation.rememberAppNavigator
import com.ricardofrango.squadify.presentation.navigation.screens
import com.ricardofrango.squadify.presentation.ui.login.LoginScreen
import com.ricardofrango.squadify.presentation.ui.register.RegisterScreen
import com.ricardofrango.squadify.presentation.ui.splash.SplashScreen
import org.koin.compose.koinInject

// Duration of the app-wide screen transition, as in the clubs app.
private const val NAV_ANIM_MS = 300

/**
 * The coach app's navigation (TECHNICAL_DECISIONS §72): sign-in, then straight to the coach's space,
 * or its first setup before there is one. No club list, Club settings, seasons or training. The shared
 * screens under the space (age group, team, match) join in the next step.
 */
@Composable
fun CoachNavigation(navigator: AppNavigator = rememberAppNavigator()) {
    // The authenticated client gave up on a request after its own refresh-and-retry: the session is
    // over, from wherever the user is.
    val sessionEventBus = koinInject<SessionEventBus>()
    LaunchedEffect(Unit) {
        sessionEventBus.forceLogout.collect {
            navigator.navigate(Screen.Login) { popUpToRoot() }
        }
    }

    val parentOwner = LocalViewModelStoreOwner.current ?: remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }

    // The clubs app's transitions: forward slides in from the right, back is the reverse, and leaving
    // the splash screen fades.
    val push: ContentTransform =
        slideInHorizontally(tween(NAV_ANIM_MS, easing = FastOutSlowInEasing)) { it } togetherWith
            (slideOutHorizontally(tween(NAV_ANIM_MS, easing = FastOutSlowInEasing)) { -it / 4 } + fadeOut(tween(NAV_ANIM_MS), targetAlpha = 0.85f))
    val pop: ContentTransform =
        (slideInHorizontally(tween(NAV_ANIM_MS, easing = FastOutSlowInEasing)) { -it / 4 } + fadeIn(tween(NAV_ANIM_MS), initialAlpha = 0.85f)) togetherWith
            slideOutHorizontally(tween(NAV_ANIM_MS, easing = FastOutSlowInEasing)) { it }
    val fade: ContentTransform = fadeIn(tween(NAV_ANIM_MS)) togetherWith fadeOut(tween(NAV_ANIM_MS))

    CompositionLocalProvider(LocalViewModelStoreOwner provides parentOwner) {
        NavDisplay(
            backStack = navigator.backStack,
            onBack = { navigator.popBackStack() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(parentOwner)
            ),
            transitionSpec = { if (initialState.entries.lastOrNull()?.metadata?.containsKey(SPLASH_ENTRY) == true) fade else push },
            popTransitionSpec = { pop },
            predictivePopTransitionSpec = { pop },
            entryProvider = remember(navigator) { coachScreens(navigator) }::entryFor
        )
    }
}

/** Every coach screen's content, and where each one navigates to. */
private fun coachScreens(navigator: AppNavigator): ScreenRegistry = screens {
    // Each home is the root of the back stack: back from it leaves the app.
    fun openAsRoot(screen: Screen) = navigator.navigate(screen) { popUpToRoot() }

    screen<Screen.Splash> {
        SplashScreen(
            // No web app, so no shared links; no pushes yet, so no back stack to open.
            deepLinkRoute = null,
            onNavigateToLogin = { openAsRoot(Screen.Login) },
            onNavigateToHome = ::openAsRoot,
            onNavigateToDeepLink = { openAsRoot(Screen.CoachHome) },
            onNavigateToBackStack = { navigator.openBackStack(it) }
        )
    }

    screen<Screen.Login> {
        LoginScreen(
            onNavigateToRegister = { navigator.navigate(Screen.Register) },
            onSignedIn = ::openAsRoot,
            // The coach server has no password reset yet.
            onNavigateToForgotPassword = null
        )
    }

    screen<Screen.Register> {
        RegisterScreen(
            onNavigateBack = { navigator.popBackStack() },
            onNavigateToDashboard = { openAsRoot(Screen.CoachHome) }
        )
    }

    screen<Screen.SpaceSetup> {
        SpaceSetupScreen(
            onSetUp = { openAsRoot(Screen.CoachHome) },
            onSignedOut = { openAsRoot(Screen.Login) }
        )
    }

    screen<Screen.CoachHome> {
        CoachHomeScreen(
            onNavigateToSetup = { openAsRoot(Screen.SpaceSetup) },
            onSignedOut = { openAsRoot(Screen.Login) }
        )
    }
}
