package com.ricardofrango.squadify.coach.presentation.navigation

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.CancellationException
import com.ricardofrango.squadify.coach.domain.GetSpacesUseCase
import com.ricardofrango.squadify.presentation.ui.components.LocalAdBanner
import com.ricardofrango.squadify.coach.presentation.ads.CoachAdBanner
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
import com.ricardofrango.squadify.coach.presentation.ui.deleteaccount.DeleteAccountScreen
import com.ricardofrango.squadify.coach.presentation.ui.home.CoachHomeScreen
import com.ricardofrango.squadify.coach.presentation.ui.invite.InviteStaffScreen
import com.ricardofrango.squadify.coach.presentation.ui.join.JoinWithCodeScreen
import com.ricardofrango.squadify.coach.presentation.ui.space.MySpaceScreen
import com.ricardofrango.squadify.coach.presentation.ui.plans.PlansRoute
import com.ricardofrango.squadify.coach.domain.OwnSpacePlan
import com.ricardofrango.squadify.presentation.ui.components.LocalPlans
import com.ricardofrango.squadify.model.SpacePlan
import com.ricardofrango.squadify.presentation.ui.components.LocalOwnSpace
import com.ricardofrango.squadify.presentation.ui.components.LocalSeePlans
import com.ricardofrango.squadify.coach.presentation.ui.spacesetup.SpaceSetupScreen
import com.ricardofrango.squadify.domain.service.SessionEventBus
import com.ricardofrango.squadify.presentation.navigation.AppNavigator
import com.ricardofrango.squadify.presentation.navigation.KEY_CLUB_STRUCTURE_CHANGED
import com.ricardofrango.squadify.presentation.navigation.KEY_PASSWORD_RESET_EMAIL
import com.ricardofrango.squadify.presentation.navigation.accountScreens
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
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import com.ricardofrango.squadify.presentation.navigation.PushLinkInbox
import com.ricardofrango.squadify.presentation.navigation.isBeforeSignIn

// Duration of the app-wide screen transition, as in the clubs app.
private const val NAV_ANIM_MS = 300

/**
 * The coach app's navigation (TECHNICAL_DECISIONS §72): sign-in (with Forgot password), then the coach's
 * age groups, from where they set up their own space, join one with a code, open their account, and open
 * the screens both apps share. No club list,
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

    // A push notification tapped while the app is running (Android: CoachActivity.onNewIntent) opens
    // here, or after Sign in if signed out; Splash opens the cold-start one itself (PushLinks.kt).
    val pushLinkInbox = koinInject<PushLinkInbox>()
    LaunchedEffect(navigator) {
        combine(pushLinkInbox.link, snapshotFlow { navigator.currentBackStackEntry }.filterNotNull()) { link, entry -> link != null && !entry.screen.isBeforeSignIn() }
            .collect { ready ->
                if (!ready) return@collect
                val link = pushLinkInbox.take() ?: return@collect
                navigator.openBackStack(coachPushLinkBackStack(link))
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

    // The own space and its plan, for the shared screens' plan boxes (the stats locks, and the Members tab,
    // Invite staff and Create age groups when the plan has no room), each with See plans.
    val ownSpacePlan = koinInject<OwnSpacePlan>()
    val ownSpace by ownSpacePlan.space.collectAsStateWithLifecycle()
    // Every plan as the server sends them, for the boxes' wording (loaded with the spaces, and by Plans).
    val plansResponse by ownSpacePlan.plans.collectAsStateWithLifecycle()
    // Home loads the spaces, and with them the own space and the plans. A notification can open a game
    // or a team with Home under it, never shown: then they're loaded here, once signed in, so the plan
    // boxes there have the plans' names and limits.
    val getSpacesUseCase = koinInject<GetSpacesUseCase>()
    LaunchedEffect(navigator) {
        snapshotFlow { navigator.currentBackStackEntry?.screen }.filterNotNull()
            .map { screen -> !screen.isBeforeSignIn() && screen != Screen.CoachHome }
            .distinctUntilChanged()
            .collect { pastHome ->
                if (!pastHome || ownSpacePlan.plans.value != null) return@collect
                try {
                    getSpacesUseCase()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Best effort: the boxes still show, without the plans' names, as before.
                }
            }
    }
    // Everyone gets See plans; with no space of their own, Plans marks none as theirs.
    val seePlans: () -> Unit = { navigator.navigate(Screen.Plans) }
    val adBanner: @Composable (String?) -> Unit = { spaceId -> CoachAdBanner(spaceId) }

    CompositionLocalProvider(
        LocalViewModelStoreOwner provides parentOwner,
        LocalOwnSpace provides ownSpace,
        LocalPlans provides plansResponse?.plans.orEmpty(),
        LocalSeePlans provides seePlans,
        // The Free plan's banner, for the shared screens' ad slot (AdBanner).
        LocalAdBanner provides adBanner
    ) {
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
            // No web app, so no shared links. A tapped notification's back stack comes from CoachSignedInHome.
            deepLinkRoute = null,
            onNavigateToLogin = { openAsRoot(Screen.Login) },
            onNavigateToHome = ::openAsRoot,
            onNavigateToDeepLink = { openAsRoot(Screen.CoachHome) },
            onNavigateToBackStack = { navigator.openBackStack(it) }
        )
    }

    screen<Screen.Login> { entry ->
        val passwordResetEmail by entry.savedStateHandle.getStateFlow<String?>(KEY_PASSWORD_RESET_EMAIL, null).collectAsStateWithLifecycle()
        LoginScreen(
            passwordResetEmail = passwordResetEmail,
            onNavigateToRegister = { navigator.navigate(Screen.Register) },
            onSignedIn = ::openAsRoot,
            onNavigateToForgotPassword = { email -> navigator.navigate(Screen.ForgotPassword(email)) }
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

    // The owner's own space: a new logo reloads Home's rows.
    screen<Screen.MySpace> {
        MySpaceScreen(
            onNavigateBack = { navigator.popBackStack() },
            onLogoChanged = { navigator.previousBackStackEntry?.savedStateHandle?.set(KEY_CLUB_STRUCTURE_CHANGED, true) },
            onSeePlans = { navigator.navigate(Screen.Plans) }
        )
    }

    screen<Screen.Plans> {
        PlansRoute(
            // Home lists the age groups the plan locks or frees.
            onPlanChanged = { navigator.backStack.firstOrNull { it.screen == Screen.CoachHome }?.savedStateHandle?.set(KEY_CLUB_STRUCTURE_CHANGED, true) },
            onNavigateBack = { navigator.popBackStack() }
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
            onOpenAccount = { navigator.navigate(Screen.Account) },
            onOpenMySpace = { navigator.navigate(Screen.MySpace) },
            onOpenPlans = { navigator.navigate(Screen.Plans) },
            refresh = changed,
            onRefreshed = { entry.savedStateHandle[KEY_CLUB_STRUCTURE_CHANGED] = false }
        )
    }

    // Account, Change email and Forgot password, as in the clubs app, with the photo.
    accountScreens(navigator)

    // From Account: deleted at once, then signed out, back to Sign in.
    screen<Screen.DeleteAccount> {
        DeleteAccountScreen(
            onNavigateBack = { navigator.popBackStack() },
            onDeleted = { openAsRoot(Screen.Login) }
        )
    }

    // The age group, team, player, opponent, match and stats screens, as in the clubs app. What the
    // coach app leaves out (training, seasons, club members) is its AppFeatures (CoachKoin).
    sharedScreens(navigator)
}
