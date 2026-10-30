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
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.ricardofrango.squadify.coach.presentation.ui.home.CoachHomeScreen
import com.ricardofrango.squadify.coach.presentation.ui.invite.InviteStaffScreen
import com.ricardofrango.squadify.coach.presentation.ui.join.JoinWithCodeScreen
import com.ricardofrango.squadify.coach.presentation.ui.spacesetup.SpaceSetupScreen
import com.ricardofrango.squadify.domain.service.SessionEventBus
import com.ricardofrango.squadify.presentation.navigation.AppNavigator
import com.ricardofrango.squadify.presentation.navigation.KEY_CLUB_STRUCTURE_CHANGED
import com.ricardofrango.squadify.presentation.navigation.SPLASH_ENTRY
import com.ricardofrango.squadify.presentation.navigation.Screen
import com.ricardofrango.squadify.presentation.navigation.ScreenRegistry
import com.ricardofrango.squadify.presentation.navigation.rememberAppNavigator
import com.ricardofrango.squadify.presentation.navigation.screens
import com.ricardofrango.squadify.presentation.navigation.sharedScreens
import com.ricardofrango.squadify.presentation.navigation.toRoute
import com.ricardofrango.squadify.presentation.ui.login.LoginScreen
import com.ricardofrango.squadify.presentation.ui.register.RegisterScreen
import com.ricardofrango.squadify.presentation.ui.splash.SplashScreen
import org.koin.compose.koinInject

// Duration of the app-wide screen transition, as in the clubs app.
private const val NAV_ANIM_MS = 300

/**
 * The coach app's navigation (TECHNICAL_DECISIONS §72): sign-in, then the coach's age groups, from where
 * they set up their own space, join one with a code, and open the screens both apps share. No club list,
 * Club settings, seasons or training.
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

    // Back to the home, which reloads: the new space's age group, or the one joined.
    fun backToHomeChanged() {
        navigator.previousBackStackEntry?.savedStateHandle?.set(KEY_CLUB_STRUCTURE_CHANGED, true)
        navigator.popBackStack()
    }

    screen<Screen.SpaceSetup> {
        SpaceSetupScreen(
            onSetUp = ::backToHomeChanged,
            onNavigateBack = { navigator.popBackStack() }
        )
    }

    screen<Screen.JoinWithCode> {
        JoinWithCodeScreen(
            onNavigateBack = { navigator.popBackStack() },
            onJoined = ::backToHomeChanged
        )
    }

    screen<Screen.InviteStaff> { entry ->
        val screen: Screen.InviteStaff = entry.toRoute()
        InviteStaffScreen(
            ageGroupId = screen.ageGroupId,
            ageGroupName = screen.ageGroupName,
            spaceName = screen.spaceName,
            onNavigateBack = { navigator.popBackStack() }
        )
    }

    screen<Screen.CoachHome> { entry ->
        // Set by the screens opened from here (an age group created or edited, a team added...).
        val changed by entry.savedStateHandle.getStateFlow(KEY_CLUB_STRUCTURE_CHANGED, false).collectAsStateWithLifecycle()
        CoachHomeScreen(
            onSignedOut = { openAsRoot(Screen.Login) },
            onOpenAgeGroup = { ageGroupId -> navigator.navigate(Screen.AgeGroupDetails(ageGroupId)) },
            onCreateAgeGroups = { spaceId, spaceName, sport ->
                navigator.navigate(Screen.CreateAgeGroups(spaceId, spaceName, sport.name))
            },
            onSetUpSpace = { navigator.navigate(Screen.SpaceSetup) },
            onJoinWithCode = { navigator.navigate(Screen.JoinWithCode) },
            refresh = changed,
            onRefreshed = { entry.savedStateHandle[KEY_CLUB_STRUCTURE_CHANGED] = false }
        )
    }

    // The age group, team, player, opponent, match and stats screens, as in the clubs app. What the
    // coach app leaves out (training, seasons, photos, club members) is its AppFeatures (CoachKoin).
    sharedScreens(navigator)
}
