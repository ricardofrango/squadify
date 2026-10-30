package com.ricardofrango.squadify.presentation.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricardofrango.squadify.domain.service.SessionEventBus
import com.ricardofrango.squadify.domain.usecase.GetClubsUseCase
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import org.koin.compose.koinInject
import com.ricardofrango.squadify.presentation.ui.changeemail.ChangeEmailScreen
import com.ricardofrango.squadify.presentation.ui.forgotpassword.ForgotPasswordScreen
import com.ricardofrango.squadify.presentation.ui.login.LoginScreen
import com.ricardofrango.squadify.presentation.ui.register.RegisterScreen
import com.ricardofrango.squadify.presentation.ui.splash.SplashScreen
import com.ricardofrango.squadify.presentation.ui.dashboard.DashboardScreen
import com.ricardofrango.squadify.presentation.ui.account.AccountScreen
import com.ricardofrango.squadify.presentation.ui.clubdetails.ClubDetailsScreen
import com.ricardofrango.squadify.presentation.ui.editclub.EditClubScreen
import com.ricardofrango.squadify.presentation.ui.clubdetails.ClubPlayersScreen
import com.ricardofrango.squadify.presentation.ui.clubmember.ClubMemberDetailsScreen
import com.ricardofrango.squadify.presentation.ui.createseason.CreateSeasonScreen
import com.ricardofrango.squadify.presentation.ui.addstafftoagegroup.AddStaffToAgeGroupScreen
import com.ricardofrango.squadify.presentation.ui.createstaff.CreateStaffScreen
import com.ricardofrango.squadify.presentation.ui.theme.ClubThemeStore
import com.ricardofrango.squadify.model.TrainingPlanLevel

// Forgot password -> Login: the email whose password was just reset (prefilled, with a confirmation).
private const val KEY_PASSWORD_RESET_EMAIL = "passwordResetEmail"

// Change email -> Account: the new address, shown with a confirmation.
private const val KEY_EMAIL_CHANGED = "emailChanged"

// Duration of the app-wide screen transition (see the NavHost below).
private const val NAV_ANIM_MS = 300

// Member details → Club settings: the members list is stale; an optional message to show there
// ("Invite to … cancelled"), since the screen that did it has already closed.
private const val KEY_MEMBERS_CHANGED = "clubMembersChanged"
private const val KEY_MEMBERS_MESSAGE = "clubMembersMessage"

@Composable
fun AppNavigation(navigator: AppNavigator = rememberAppNavigator()) {
    // Read (and, on web, cleared from the visible URL) exactly once, during this first composition --
    // strictly before the LaunchedEffect below binds the browser history; the deep link is instead
    // opened explicitly, and only once the session check confirms it's safe to, by SplashViewModel
    // below. See WebDeepLinking.kt / TECHNICAL_DECISIONS.md.
    val deepLinkRoute = remember { initialDeepLinkRouteOrNull() }

    LaunchedEffect(navigator) {
        navigator.bindToBrowserHistoryIfWeb()
    }
    // Fires when the authenticated HTTP client (see Koin.kt) gives up on a request after its own
    // refresh-and-retry -- the session is truly dead, from wherever the user currently is in the app,
    // not just from Splash's own cold-start check or Account's manual logout button.
    val sessionEventBus = koinInject<SessionEventBus>()
    LaunchedEffect(Unit) {
        sessionEventBus.forceLogout.collect {
            navigator.navigate(Screen.Login) { popUpToRoot() }
        }
    }

    // A push notification tapped while the app is already running (Android: MainActivity.onNewIntent)
    // opens here, or after Login if the user is signed out. Splash opens the cold-start one itself, so
    // nothing happens while it (or a sign-in screen) is showing. See PushLinks.kt / §56.
    val pushLinkInbox = koinInject<PushLinkInbox>()
    val getClubsUseCase = koinInject<GetClubsUseCase>()
    val clubThemeStore = koinInject<ClubThemeStore>()
    LaunchedEffect(navigator) {
        combine(pushLinkInbox.link, snapshotFlow { navigator.currentBackStackEntry }.filterNotNull()) { link, entry -> link != null && !entry.screen.isBeforeSignIn() }
            .collect { ready ->
                if (!ready) return@collect
                val link = pushLinkInbox.take() ?: return@collect
                // Keeps the clubs' colours, so the screens opened below wear their club's (§58).
                val onlyClubId = try {
                    getClubsUseCase().also(clubThemeStore::remember).singleOrNull()?.id
                } catch (e: Exception) {
                    null
                }
                navigator.openBackStack(pushLinkBackStack(link, onlyClubId))
            }
    }

    // Each screen gets its own view models (koinViewModel) and remembered state, kept while it's in the
    // back stack and cleared when it's popped, as each NavBackStackEntry had. The view models' parent
    // is the platform's owner (the activity on Android); where a platform provides none, the app's own.
    val parentOwner = LocalViewModelStoreOwner.current ?: remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }

    // One horizontal "push" for the whole app: forward, the new screen slides in from the right while
    // the one below drifts a quarter-width left and dims slightly; back is the exact reverse. Leaving
    // the splash screen stays a plain fade -- sliding in from a splash reads as odd.
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
        entryProvider = remember(navigator, deepLinkRoute) { appScreens(navigator, deepLinkRoute) }::entryFor
    )
    }
}

/** Every screen's content, and where each one navigates to. */
private fun appScreens(navigator: AppNavigator, deepLinkRoute: String?): ScreenRegistry = screens {
        screen<Screen.Splash> {
            SplashScreen(
                deepLinkRoute = deepLinkRoute,
                onNavigateToLogin = {
                    navigator.navigate(Screen.Login) {
                        popUpTo(Screen.Splash) { inclusive = true }
                    }
                },
                onNavigateToHome = { home ->
                    navigator.navigate(home) {
                        popUpTo(Screen.Splash) { inclusive = true }
                    }
                },
                // A link that isn't a screen (any more) opens My clubs instead.
                onNavigateToDeepLink = { route ->
                    navigator.navigate(screenFromRoute(route) ?: Screen.Dashboard) {
                        popUpTo(Screen.Splash) { inclusive = true }
                    }
                },
                onNavigateToBackStack = { screens -> navigator.openBackStack(screens) }
            )
        }

        screen<Screen.Login> { entry ->
            val passwordResetEmail by entry.savedStateHandle.getStateFlow<String?>(KEY_PASSWORD_RESET_EMAIL, null).collectAsStateWithLifecycle()
            LoginScreen(
                passwordResetEmail = passwordResetEmail,
                onNavigateToForgotPassword = { email -> navigator.navigate(Screen.ForgotPassword(email)) },
                onNavigateToRegister = {
                    navigator.navigate(Screen.Register)
                },
                onSignedIn = { home ->
                    navigator.navigate(home) {
                        popUpTo(Screen.Login) { inclusive = true }
                    }
                }
            )
        }
        
        screen<Screen.ForgotPassword> { entry ->
            val loginEntry = remember(entry) { navigator.previousBackStackEntry }
            ForgotPasswordScreen(
                initialEmail = entry.toRoute<Screen.ForgotPassword>().email,
                onNavigateBack = { navigator.popBackStack() },
                onPasswordReset = { email ->
                    navigator.setIfAlive(loginEntry, KEY_PASSWORD_RESET_EMAIL, email)
                    navigator.popBackStack()
                }
            )
        }

        screen<Screen.ChangeEmail> { entry ->
            val accountEntry = remember(entry) { navigator.previousBackStackEntry }
            ChangeEmailScreen(
                currentEmail = entry.toRoute<Screen.ChangeEmail>().currentEmail.ifEmpty { null },
                onNavigateBack = { navigator.popBackStack() },
                onEmailChanged = { email ->
                    navigator.setIfAlive(accountEntry, KEY_EMAIL_CHANGED, email)
                    navigator.popBackStack()
                },
                onSignedOut = {
                    navigator.navigate(Screen.Login) { popUpToRoot() }
                }
            )
        }

        screen<Screen.Register> {
            RegisterScreen(
                onNavigateBack = {
                    navigator.popBackStack()
                },
                onNavigateToDashboard = {
                    navigator.navigate(Screen.Dashboard) {
                        popUpTo(Screen.Login) { inclusive = true }
                    }
                }
            )
        }
        
        screen<Screen.Dashboard> {
            DashboardScreen(
                onNavigateToClub = { clubId ->
                    navigator.navigate(Screen.ClubDetails(clubId))
                },
                onNavigateToOnlyClub = { clubId ->
                    // The clubs list has nothing to offer a single-club user, so pop it off the back
                    // stack entirely -- ClubDetailsScreen(isRootClub = true) then has no back button.
                    navigator.navigate(Screen.ClubDetails(clubId, isRootClub = true)) {
                        popUpTo(Screen.Dashboard) { inclusive = true }
                    }
                },
                onNavigateToAccount = {
                    navigator.navigate(Screen.Account)
                }
            )
        }

        screen<Screen.ClubDetails> { backStackEntry ->
            val screen: Screen.ClubDetails = backStackEntry.toRoute()
            val structureChanged by backStackEntry.savedStateHandle
                .getStateFlow(KEY_CLUB_STRUCTURE_CHANGED, false)
                .collectAsStateWithLifecycle()
            if (structureChanged) {
                // Consume the flag so it doesn't keep forcing reloads on every recomposition.
                backStackEntry.savedStateHandle[KEY_CLUB_STRUCTURE_CHANGED] = false
            }
            ClubDetailsScreen(
                clubId = screen.clubId,
                showBackButton = !screen.isRootClub,
                onNavigateBack = {
                    navigator.popBackStack()
                },
                onNavigateToTeam = { teamId ->
                    navigator.navigate(Screen.TeamDetails(teamId))
                },
                onNavigateToPlayer = { playerId ->
                    navigator.navigate(Screen.PlayerDetails(playerId))
                },
                onNavigateToCreateSeason = { clubName ->
                    navigator.navigate(Screen.CreateSeason(screen.clubId, clubName))
                },
                onNavigateToCreateAgeGroups = { clubName, sport, initialSeasonId ->
                    navigator.navigate(
                        Screen.CreateAgeGroups(screen.clubId, clubName, sport.name, initialSeasonId)
                    )
                },
                onNavigateToCreateTeams = { clubName, sport, ageGroupId ->
                    navigator.navigate(Screen.CreateTeams(screen.clubId, clubName, sport.name, ageGroupId))
                },
                onNavigateToAddPlayersToAgeGroup = { clubName ->
                    navigator.navigate(Screen.AddPlayersToAgeGroup(screen.clubId, clubName))
                },
                onNavigateToCreatePlayers = { clubName, ageGroupId ->
                    navigator.navigate(Screen.CreatePlayers(screen.clubId, clubName, ageGroupId))
                },
                onNavigateToCreateStaff = { clubName ->
                    navigator.navigate(Screen.CreateStaff(screen.clubId, clubName))
                },
                onNavigateToAgeGroup = { ageGroupId ->
                    navigator.navigate(Screen.AgeGroupDetails(ageGroupId))
                },
                onNavigateToMatch = { matchId ->
                    navigator.navigate(Screen.MatchDetails(matchId))
                },
                onNavigateToAccount = {
                    navigator.navigate(Screen.Account)
                },
                onNavigateToEditClub = {
                    navigator.navigate(Screen.EditClub(screen.clubId))
                },
                onNavigateToPlayers = {
                    navigator.navigate(Screen.ClubPlayers(screen.clubId))
                },
                onNavigateToOpponents = {
                    navigator.navigate(Screen.Opponents(screen.clubId))
                },
                onNavigateToRateSession = { sessionId -> navigator.navigate(Screen.RateTraining(sessionId)) },
                onNavigateToEditPlan = { ageGroupId, level, start ->
                    navigator.navigate(
                        when (level) {
                            TrainingPlanLevel.SEASON -> Screen.EditSeasonPlan(ageGroupId)
                            TrainingPlanLevel.MONTH -> Screen.EditMonthPlan(ageGroupId, start.toString())
                            TrainingPlanLevel.WEEK -> Screen.EditWeekPlan(ageGroupId, start.toString())
                        }
                    )
                },
                // Back to the "My clubs" list. Normally it's already underneath (the club was opened
                // from it), so pop back to it -- pushing a new one left two lists stacked, and back
                // from the list went to the other list. A user who started straight in their only
                // club (isRootClub: the list was popped) gets the list in place of this screen.
                onSwitchClub = {
                    if (!navigator.popBackStack<Screen.Dashboard>(inclusive = false)) {
                        navigator.navigate(Screen.Dashboard) {
                            popUpTo<Screen.ClubDetails> { inclusive = true }
                        }
                    }
                },
                // Clear the whole back stack, so back from Sign in can't return into the club.
                onLoggedOut = {
                    navigator.navigate(Screen.Login) {
                        popUpToRoot()
                    }
                },
                refreshClubDetails = structureChanged
            )
        }

        screen<Screen.ClubPlayers> { backStackEntry ->
            val screen: Screen.ClubPlayers = backStackEntry.toRoute()
            // Set by the create / add-players screens opened from here, so the list reloads on return.
            val structureChanged by backStackEntry.savedStateHandle
                .getStateFlow(KEY_CLUB_STRUCTURE_CHANGED, false)
                .collectAsStateWithLifecycle()
            if (structureChanged) {
                navigator.consumeAndCascadeStructureChanged(backStackEntry)
            }
            ClubPlayersScreen(
                clubId = screen.clubId,
                onNavigateBack = { navigator.popBackStack() },
                onOpenPlayer = { playerId -> navigator.navigate(Screen.PlayerDetails(playerId)) },
                onCreateAgeGroups = { clubName, sport, seasonId ->
                    navigator.navigate(Screen.CreateAgeGroups(screen.clubId, clubName, sport.name, seasonId))
                },
                onCreatePlayers = { clubName -> navigator.navigate(Screen.CreatePlayers(screen.clubId, clubName)) },
                onAddPlayersToAgeGroup = { clubName -> navigator.navigate(Screen.AddPlayersToAgeGroup(screen.clubId, clubName)) },
                refresh = structureChanged
            )
        }

        screen<Screen.EditClub> { backStackEntry ->
            val screen: Screen.EditClub = backStackEntry.toRoute()
            val membersChanged by backStackEntry.savedStateHandle
                .getStateFlow(KEY_MEMBERS_CHANGED, false)
                .collectAsStateWithLifecycle()
            val membersMessage by backStackEntry.savedStateHandle
                .getStateFlow<String?>(KEY_MEMBERS_MESSAGE, null)
                .collectAsStateWithLifecycle()
            EditClubScreen(
                clubId = screen.clubId,
                onNavigateBack = { navigator.popBackStack() },
                onClubUpdated = {
                    navigator.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(KEY_CLUB_STRUCTURE_CHANGED, true)
                },
                onOpenMember = { userId -> navigator.navigate(Screen.ClubMemberDetails(screen.clubId, userId = userId)) },
                onOpenInvite = { inviteId -> navigator.navigate(Screen.ClubMemberDetails(screen.clubId, inviteId = inviteId)) },
                refreshMembers = membersChanged,
                membersMessage = membersMessage,
                onMembersRefreshed = {
                    backStackEntry.savedStateHandle[KEY_MEMBERS_CHANGED] = false
                    backStackEntry.savedStateHandle[KEY_MEMBERS_MESSAGE] = null
                }
            )
        }

        screen<Screen.ClubMemberDetails> { backStackEntry ->
            val screen: Screen.ClubMemberDetails = backStackEntry.toRoute()
            ClubMemberDetailsScreen(
                clubId = screen.clubId,
                userId = screen.userId,
                inviteId = screen.inviteId,
                onNavigateBack = { navigator.popBackStack() },
                onMembersChanged = { message ->
                    navigator.previousBackStackEntry?.savedStateHandle?.let {
                        it[KEY_MEMBERS_CHANGED] = true
                        if (message != null) it[KEY_MEMBERS_MESSAGE] = message
                        // Opened from an age group's or team's staff: their staff list may be stale too.
                        it[KEY_CLUB_STRUCTURE_CHANGED] = true
                    }
                },
                onReturnToClub = {
                    // Past Club settings, which a demoted viewer may no longer be allowed to see.
                    if (!navigator.popBackStack<Screen.EditClub>(inclusive = true)) navigator.popBackStack()
                },
                onOpenAgeGroup = { ageGroupId -> navigator.navigate(Screen.AgeGroupDetails(ageGroupId)) },
                onOpenPlayer = { playerId -> navigator.navigate(Screen.PlayerDetails(playerId)) }
            )
        }

        screen<Screen.CreateSeason> { backStackEntry ->
            val screen: Screen.CreateSeason = backStackEntry.toRoute()
            CreateSeasonScreen(
                clubId = screen.clubId,
                clubName = screen.clubName,
                onNavigateBack = {
                    navigator.popBackStack()
                },
                onSeasonCreated = {
                    navigator.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(KEY_CLUB_STRUCTURE_CHANGED, true)
                    navigator.popBackStack()
                }
            )
        }

        screen<Screen.CreateStaff> { backStackEntry ->
            val screen: Screen.CreateStaff = backStackEntry.toRoute()
            CreateStaffScreen(
                clubId = screen.clubId,
                clubName = screen.clubName,
                onNavigateBack = {
                    navigator.popBackStack()
                },
                onStaffCreated = {
                    navigator.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(KEY_CLUB_STRUCTURE_CHANGED, true)
                    navigator.popBackStack()
                }
            )
        }

        screen<Screen.AddStaffToAgeGroup> { backStackEntry ->
            val screen: Screen.AddStaffToAgeGroup = backStackEntry.toRoute()
            // Set by CreateStaff when the "New staff member" shortcut is used -- reload the eligible
            // list so the just-registered members show up, and pass the signal on down (ClubDetails /
            // AgeGroupDetails) so their state refreshes too. Mirrors AddPlayersToAgeGroup.
            val structureChanged by backStackEntry.savedStateHandle
                .getStateFlow(KEY_CLUB_STRUCTURE_CHANGED, false)
                .collectAsStateWithLifecycle()
            if (structureChanged) {
                navigator.consumeAndCascadeStructureChanged(backStackEntry)
            }
            AddStaffToAgeGroupScreen(
                clubId = screen.clubId,
                clubName = screen.clubName,
                initialAgeGroupId = screen.initialAgeGroupId,
                onNavigateBack = {
                    navigator.popBackStack()
                },
                onNavigateToCreateStaff = {
                    navigator.navigate(Screen.CreateStaff(screen.clubId, screen.clubName))
                },
                onStaffAdded = {
                    navigator.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(KEY_CLUB_STRUCTURE_CHANGED, true)
                    navigator.popBackStack()
                },
                refreshOnReturn = structureChanged
            )
        }

        screen<Screen.Account> { entry ->
            val changedEmail by entry.savedStateHandle.getStateFlow<String?>(KEY_EMAIL_CHANGED, null).collectAsStateWithLifecycle()
            AccountScreen(
                changedEmail = changedEmail,
                onNavigateToChangeEmail = { email -> navigator.navigate(Screen.ChangeEmail(email)) },
                onNavigateBack = {
                    navigator.popBackStack()
                },
                // Clear the whole back stack: a single-club user has no My clubs underneath, and a
                // club left under Sign in would also draw it in that club's colours.
                onNavigateToLogin = {
                    navigator.navigate(Screen.Login) {
                        popUpToRoot()
                    }
                }
            )
        }

        // Every screen both apps have (app/shared's SharedScreens.kt).
        sharedScreens(navigator)
    }
