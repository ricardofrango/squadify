package com.ricardofrango.squadify.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.ricardofrango.squadify.domain.service.SessionEventBus
import com.ricardofrango.squadify.domain.usecase.GetClubsUseCase
import kotlinx.coroutines.flow.combine
import com.ricardofrango.squadify.model.StatsFilter
import com.ricardofrango.squadify.model.SportType
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
import com.ricardofrango.squadify.presentation.ui.editteam.EditTeamScreen
import com.ricardofrango.squadify.presentation.ui.editagegroup.EditAgeGroupScreen
import com.ricardofrango.squadify.presentation.ui.clubmember.ClubMemberDetailsScreen
import com.ricardofrango.squadify.presentation.ui.createseason.CreateSeasonScreen
import com.ricardofrango.squadify.presentation.ui.createteams.CreateTeamsScreen
import com.ricardofrango.squadify.presentation.ui.addplayerstoagegroup.AddPlayersToAgeGroupScreen
import com.ricardofrango.squadify.presentation.ui.createplayers.CreatePlayersScreen
import com.ricardofrango.squadify.presentation.ui.addstafftoagegroup.AddStaffToAgeGroupScreen
import com.ricardofrango.squadify.presentation.ui.createstaff.CreateStaffScreen
import com.ricardofrango.squadify.presentation.ui.agegroupdetails.AgeGroupDetailsScreen
import com.ricardofrango.squadify.presentation.ui.createagegroups.CreateAgeGroupsScreen
import com.ricardofrango.squadify.presentation.ui.teamdetails.TeamDetailsScreen
import com.ricardofrango.squadify.presentation.ui.fullstats.FullStatsScreen
import com.ricardofrango.squadify.presentation.ui.theme.ClubThemeStore
import com.ricardofrango.squadify.presentation.ui.creatematch.CreateMatchScreen
import com.ricardofrango.squadify.presentation.ui.opponents.OpponentDetailsScreen
import com.ricardofrango.squadify.presentation.ui.opponents.OpponentEditScreen
import com.ricardofrango.squadify.presentation.ui.opponents.OpponentFormScreen
import com.ricardofrango.squadify.presentation.ui.opponents.OpponentsScreen
import com.ricardofrango.squadify.presentation.ui.training.CreateSessionsScreen
import com.ricardofrango.squadify.presentation.ui.training.ExerciseLibraryScreen
import com.ricardofrango.squadify.presentation.ui.training.RatePlayersScreen
import com.ricardofrango.squadify.presentation.ui.training.SessionDetailsScreen
import com.ricardofrango.squadify.presentation.ui.training.SessionEditScreen
import com.ricardofrango.squadify.presentation.ui.training.MonthPlanScreen
import com.ricardofrango.squadify.presentation.ui.training.PeriodPlanEditScreen
import com.ricardofrango.squadify.presentation.ui.training.SeasonPlanEditScreen
import com.ricardofrango.squadify.model.TrainingPlanLevel
import kotlinx.datetime.LocalDate
import com.ricardofrango.squadify.presentation.ui.training.SeasonPlanScreen
import com.ricardofrango.squadify.presentation.ui.training.WeekPlanScreen
import com.ricardofrango.squadify.presentation.ui.matchdetails.MatchDetailsScreen
import com.ricardofrango.squadify.presentation.ui.matchreport.MatchReportScreen
import com.ricardofrango.squadify.presentation.ui.matchrecord.MatchRecordScreen
import com.ricardofrango.squadify.presentation.ui.livematch.LiveMatchScreen
import com.ricardofrango.squadify.presentation.ui.playerdetails.PlayerDetailsScreen
import com.ricardofrango.squadify.presentation.ui.editplayer.EditPlayerScreen

// Key used on a back-stack entry's SavedStateHandle to signal "a season, age group, team, player, or
// match was just created/changed, force a reload" -- the standard Compose Navigation pattern for a
// screen to return a result to the screen that pushed it. See ClubDetailsScreen's `refreshClubDetails`
// doc.
private const val KEY_CLUB_STRUCTURE_CHANGED = "clubStructureChanged"

// Forgot password -> Login: the email whose password was just reset (prefilled, with a confirmation).
private const val KEY_PASSWORD_RESET_EMAIL = "passwordResetEmail"

// Change email -> Account: the new address, shown with a confirmation.
private const val KEY_EMAIL_CHANGED = "emailChanged"

// Opponents (SELECT) -> Schedule match: the id of the opponent just picked.
private const val KEY_OPPONENT_PICKED = "opponentPicked"

// Create opponent (SELECT) -> Schedule match: the new opponent's name, for "… created and selected".
private const val KEY_OPPONENT_CREATED = "opponentCreated"

// Create sessions -> Age Group Details: how many were created, for "24 sessions created".
private const val KEY_TRAINING_MESSAGE = "trainingMessage"

// Duration of the app-wide screen transition (see the NavHost below).
private const val NAV_ANIM_MS = 300

// Member details → Club settings: the members list is stale; an optional message to show there
// ("Invite to … cancelled"), since the screen that did it has already closed.
private const val KEY_MEMBERS_CHANGED = "clubMembersChanged"
private const val KEY_MEMBERS_MESSAGE = "clubMembersMessage"

/**
 * Sets [key] on this entry's SavedStateHandle only while the entry is alive. Navigation throws if the
 * handle is touched before the entry is attached or after it's destroyed -- and an entry captured
 * earlier can be destroyed by the time it's used: when the activity is relaunched (a configuration
 * change, e.g. unlocking the phone mid-match), the back stack is torn down before the composition's
 * onDispose callbacks run. Skipping the flag then is safe: nothing is returning to that screen at
 * that moment, and the relaunch recomposes the screen on top, which registers its onDispose again
 * against the restored (alive) entry -- so the flag is still set when the user really leaves.
 */
private fun NavBackStackEntry.setIfAlive(key: String, value: Any?) {
    if (lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) savedStateHandle[key] = value
}

/**
 * Consume the "structure changed" result on [entry] and cascade it one level further down the back
 * stack. A Create* screen only ever sets the flag on its immediate `previousBackStackEntry`, so when
 * the create action is taken two or more screens deep (e.g. creating a team from AgeGroupDetails,
 * which itself sits on top of ClubDetails) the screens underneath the launcher would otherwise never
 * learn they're stale. Each intermediate detail screen forwards the signal on as it consumes it, so
 * the reload reaches every screen below when the user pops back.
 */
private fun NavController.consumeAndCascadeStructureChanged(entry: NavBackStackEntry) {
    entry.savedStateHandle[KEY_CLUB_STRUCTURE_CHANGED] = false
    previousBackStackEntry?.savedStateHandle?.set(KEY_CLUB_STRUCTURE_CHANGED, true)
}

@Composable
fun AppNavigation(navController: NavHostController = rememberNavController()) {
    // Read (and, on web, cleared from the visible URL) exactly once, during this first composition --
    // strictly before the LaunchedEffect below runs, so bindToBrowserHistoryIfWeb's own default
    // "navigate straight to the initial URL fragment" behavior finds nothing left to act on; the deep
    // link is instead handled explicitly, and only once the session check confirms it's safe to, by
    // SplashViewModel below. See WebDeepLinking.kt / TECHNICAL_DECISIONS.md.
    val deepLinkRoute = remember { initialDeepLinkRouteOrNull() }

    LaunchedEffect(navController) {
        navController.bindToBrowserHistoryIfWeb()
    }
    // Fires when the authenticated HTTP client (see Koin.kt) gives up on a request after its own
    // refresh-and-retry -- the session is truly dead, from wherever the user currently is in the app,
    // not just from Splash's own cold-start check or Account's manual logout button.
    val sessionEventBus = koinInject<SessionEventBus>()
    LaunchedEffect(Unit) {
        sessionEventBus.forceLogout.collect {
            navController.navigate(Screen.Login) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    // A push notification tapped while the app is already running (Android: MainActivity.onNewIntent)
    // opens here, or after Login if the user is signed out. Splash opens the cold-start one itself, so
    // nothing happens while it (or a sign-in screen) is showing. See PushLinks.kt / §56.
    val pushLinkInbox = koinInject<PushLinkInbox>()
    val getClubsUseCase = koinInject<GetClubsUseCase>()
    val clubThemeStore = koinInject<ClubThemeStore>()
    LaunchedEffect(navController) {
        combine(pushLinkInbox.link, navController.currentBackStackEntryFlow) { link, entry -> link != null && !entry.destination.isBeforeSignIn() }
            .collect { ready ->
                if (!ready) return@collect
                val link = pushLinkInbox.take() ?: return@collect
                // Keeps the clubs' colours, so the screens opened below wear their club's (§58).
                val onlyClubId = try {
                    getClubsUseCase().also(clubThemeStore::remember).singleOrNull()?.id
                } catch (e: Exception) {
                    null
                }
                navController.openBackStack(pushLinkBackStack(link, onlyClubId))
            }
    }

    // One horizontal "push" for the whole app: forward, the new screen slides in from the right while
    // the one below drifts a quarter-width left and dims slightly; back is the exact reverse. Leaving
    // the splash screen stays a plain fade -- sliding in from a splash reads as odd.
    val fromSplash: AnimatedContentTransitionScope<NavBackStackEntry>.() -> Boolean =
        { initialState.destination.hasRoute<Screen.Splash>() }
    NavHost(
        navController = navController,
        startDestination = Screen.Splash,
        enterTransition = {
            if (fromSplash()) fadeIn(tween(NAV_ANIM_MS))
            else slideInHorizontally(tween(NAV_ANIM_MS, easing = FastOutSlowInEasing)) { it }
        },
        exitTransition = {
            if (fromSplash()) fadeOut(tween(NAV_ANIM_MS))
            else slideOutHorizontally(tween(NAV_ANIM_MS, easing = FastOutSlowInEasing)) { -it / 4 } + fadeOut(tween(NAV_ANIM_MS), targetAlpha = 0.85f)
        },
        popEnterTransition = {
            slideInHorizontally(tween(NAV_ANIM_MS, easing = FastOutSlowInEasing)) { -it / 4 } + fadeIn(tween(NAV_ANIM_MS), initialAlpha = 0.85f)
        },
        popExitTransition = {
            slideOutHorizontally(tween(NAV_ANIM_MS, easing = FastOutSlowInEasing)) { it }
        }
    ) {
        screen<Screen.Splash> {
            SplashScreen(
                deepLinkRoute = deepLinkRoute,
                onNavigateToLogin = {
                    navController.navigate(Screen.Login) {
                        popUpTo(Screen.Splash) { inclusive = true }
                    }
                },
                onNavigateToDashboard = {
                    navController.navigate(Screen.Dashboard) {
                        popUpTo(Screen.Splash) { inclusive = true }
                    }
                },
                onNavigateToOnlyClub = { clubId ->
                    navController.navigate(Screen.ClubDetails(clubId, isRootClub = true)) {
                        popUpTo(Screen.Splash) { inclusive = true }
                    }
                },
                onNavigateToDeepLink = { route ->
                    navController.navigate(route) {
                        popUpTo(Screen.Splash) { inclusive = true }
                    }
                },
                onNavigateToBackStack = { screens -> navController.openBackStack(screens) }
            )
        }

        screen<Screen.Login> { entry ->
            val passwordResetEmail by entry.savedStateHandle.getStateFlow<String?>(KEY_PASSWORD_RESET_EMAIL, null).collectAsStateWithLifecycle()
            LoginScreen(
                passwordResetEmail = passwordResetEmail,
                onNavigateToForgotPassword = { email -> navController.navigate(Screen.ForgotPassword(email)) },
                onNavigateToRegister = {
                    navController.navigate(Screen.Register)
                },
                onNavigateToDashboard = {
                    navController.navigate(Screen.Dashboard) {
                        popUpTo(Screen.Login) { inclusive = true }
                    }
                },
                onNavigateToOnlyClub = { clubId ->
                    navController.navigate(Screen.ClubDetails(clubId, isRootClub = true)) {
                        popUpTo(Screen.Login) { inclusive = true }
                    }
                }
            )
        }
        
        screen<Screen.ForgotPassword> { entry ->
            val loginEntry = remember(entry) { navController.previousBackStackEntry }
            ForgotPasswordScreen(
                initialEmail = entry.toRoute<Screen.ForgotPassword>().email,
                onNavigateBack = { navController.popBackStack() },
                onPasswordReset = { email ->
                    loginEntry?.setIfAlive(KEY_PASSWORD_RESET_EMAIL, email)
                    navController.popBackStack()
                }
            )
        }

        screen<Screen.ChangeEmail> { entry ->
            val accountEntry = remember(entry) { navController.previousBackStackEntry }
            ChangeEmailScreen(
                currentEmail = entry.toRoute<Screen.ChangeEmail>().currentEmail.ifEmpty { null },
                onNavigateBack = { navController.popBackStack() },
                onEmailChanged = { email ->
                    accountEntry?.setIfAlive(KEY_EMAIL_CHANGED, email)
                    navController.popBackStack()
                },
                onSignedOut = {
                    navController.navigate(Screen.Login) { popUpTo(navController.graph.id) { inclusive = true } }
                }
            )
        }

        screen<Screen.Register> {
            RegisterScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToDashboard = {
                    navController.navigate(Screen.Dashboard) {
                        popUpTo(Screen.Login) { inclusive = true }
                    }
                }
            )
        }
        
        screen<Screen.Dashboard> {
            DashboardScreen(
                onNavigateToClub = { clubId ->
                    navController.navigate(Screen.ClubDetails(clubId))
                },
                onNavigateToOnlyClub = { clubId ->
                    // The clubs list has nothing to offer a single-club user, so pop it off the back
                    // stack entirely -- ClubDetailsScreen(isRootClub = true) then has no back button.
                    navController.navigate(Screen.ClubDetails(clubId, isRootClub = true)) {
                        popUpTo(Screen.Dashboard) { inclusive = true }
                    }
                },
                onNavigateToAccount = {
                    navController.navigate(Screen.Account)
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
                    navController.popBackStack()
                },
                onNavigateToTeam = { teamId ->
                    navController.navigate(Screen.TeamDetails(teamId))
                },
                onNavigateToPlayer = { playerId ->
                    navController.navigate(Screen.PlayerDetails(playerId))
                },
                onNavigateToCreateSeason = { clubName ->
                    navController.navigate(Screen.CreateSeason(screen.clubId, clubName))
                },
                onNavigateToCreateAgeGroups = { clubName, sport, initialSeasonId ->
                    navController.navigate(
                        Screen.CreateAgeGroups(screen.clubId, clubName, sport.name, initialSeasonId)
                    )
                },
                onNavigateToCreateTeams = { clubName, sport, ageGroupId ->
                    navController.navigate(Screen.CreateTeams(screen.clubId, clubName, sport.name, ageGroupId))
                },
                onNavigateToAddPlayersToAgeGroup = { clubName ->
                    navController.navigate(Screen.AddPlayersToAgeGroup(screen.clubId, clubName))
                },
                onNavigateToCreatePlayers = { clubName, ageGroupId ->
                    navController.navigate(Screen.CreatePlayers(screen.clubId, clubName, ageGroupId))
                },
                onNavigateToCreateStaff = { clubName ->
                    navController.navigate(Screen.CreateStaff(screen.clubId, clubName))
                },
                onNavigateToAgeGroup = { ageGroupId ->
                    navController.navigate(Screen.AgeGroupDetails(ageGroupId))
                },
                onNavigateToMatch = { matchId ->
                    navController.navigate(Screen.MatchDetails(matchId))
                },
                onNavigateToAccount = {
                    navController.navigate(Screen.Account)
                },
                onNavigateToEditClub = {
                    navController.navigate(Screen.EditClub(screen.clubId))
                },
                onNavigateToPlayers = {
                    navController.navigate(Screen.ClubPlayers(screen.clubId))
                },
                onNavigateToOpponents = {
                    navController.navigate(Screen.Opponents(screen.clubId))
                },
                onNavigateToRateSession = { sessionId -> navController.navigate(Screen.RateTraining(sessionId)) },
                onNavigateToEditPlan = { ageGroupId, level, start ->
                    navController.navigate(
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
                    if (!navController.popBackStack<Screen.Dashboard>(inclusive = false)) {
                        navController.navigate(Screen.Dashboard) {
                            popUpTo<Screen.ClubDetails> { inclusive = true }
                        }
                    }
                },
                // Clear the whole back stack, so back from Sign in can't return into the club.
                onLoggedOut = {
                    navController.navigate(Screen.Login) {
                        popUpTo(navController.graph.id) { inclusive = true }
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
                navController.consumeAndCascadeStructureChanged(backStackEntry)
            }
            ClubPlayersScreen(
                clubId = screen.clubId,
                onNavigateBack = { navController.popBackStack() },
                onOpenPlayer = { playerId -> navController.navigate(Screen.PlayerDetails(playerId)) },
                onCreateAgeGroups = { clubName, sport, seasonId ->
                    navController.navigate(Screen.CreateAgeGroups(screen.clubId, clubName, sport.name, seasonId))
                },
                onCreatePlayers = { clubName -> navController.navigate(Screen.CreatePlayers(screen.clubId, clubName)) },
                onAddPlayersToAgeGroup = { clubName -> navController.navigate(Screen.AddPlayersToAgeGroup(screen.clubId, clubName)) },
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
                onNavigateBack = { navController.popBackStack() },
                onClubUpdated = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(KEY_CLUB_STRUCTURE_CHANGED, true)
                },
                onOpenMember = { userId -> navController.navigate(Screen.ClubMemberDetails(screen.clubId, userId = userId)) },
                onOpenInvite = { inviteId -> navController.navigate(Screen.ClubMemberDetails(screen.clubId, inviteId = inviteId)) },
                refreshMembers = membersChanged,
                membersMessage = membersMessage,
                onMembersRefreshed = {
                    backStackEntry.savedStateHandle[KEY_MEMBERS_CHANGED] = false
                    backStackEntry.savedStateHandle[KEY_MEMBERS_MESSAGE] = null
                }
            )
        }

        screen<Screen.EditAgeGroup> { backStackEntry ->
            val screen: Screen.EditAgeGroup = backStackEntry.toRoute()
            EditAgeGroupScreen(
                ageGroupId = screen.ageGroupId,
                onNavigateBack = { navController.popBackStack() },
                onAgeGroupUpdated = {
                    // Age Group Details (and the club below it) show the name and years: reload on return.
                    navController.previousBackStackEntry?.savedStateHandle?.set(KEY_CLUB_STRUCTURE_CHANGED, true)
                    navController.popBackStack()
                }
            )
        }

        screen<Screen.EditTeam> { backStackEntry ->
            val screen: Screen.EditTeam = backStackEntry.toRoute()
            EditTeamScreen(
                teamId = screen.teamId,
                onNavigateBack = { navController.popBackStack() },
                onTeamUpdated = {
                    // Team Details (and the age group / club below it) show the name: reload on return.
                    navController.previousBackStackEntry?.savedStateHandle?.set(KEY_CLUB_STRUCTURE_CHANGED, true)
                    navController.popBackStack()
                },
                onTeamDeleted = {
                    // Team Details is gone with the team: back to what opened it (the age group), which
                    // reloads and passes the refresh down (its teams, matches and the club's counts changed).
                    if (!navController.popBackStack<Screen.TeamDetails>(inclusive = true)) navController.popBackStack()
                    navController.currentBackStackEntry?.savedStateHandle?.set(KEY_CLUB_STRUCTURE_CHANGED, true)
                }
            )
        }

        screen<Screen.ClubMemberDetails> { backStackEntry ->
            val screen: Screen.ClubMemberDetails = backStackEntry.toRoute()
            ClubMemberDetailsScreen(
                clubId = screen.clubId,
                userId = screen.userId,
                inviteId = screen.inviteId,
                onNavigateBack = { navController.popBackStack() },
                onMembersChanged = { message ->
                    navController.previousBackStackEntry?.savedStateHandle?.let {
                        it[KEY_MEMBERS_CHANGED] = true
                        if (message != null) it[KEY_MEMBERS_MESSAGE] = message
                        // Opened from an age group's or team's staff: their staff list may be stale too.
                        it[KEY_CLUB_STRUCTURE_CHANGED] = true
                    }
                },
                onReturnToClub = {
                    // Past Club settings, which a demoted viewer may no longer be allowed to see.
                    if (!navController.popBackStack<Screen.EditClub>(inclusive = true)) navController.popBackStack()
                },
                onOpenAgeGroup = { ageGroupId -> navController.navigate(Screen.AgeGroupDetails(ageGroupId)) },
                onOpenPlayer = { playerId -> navController.navigate(Screen.PlayerDetails(playerId)) }
            )
        }

        screen<Screen.CreateSeason> { backStackEntry ->
            val screen: Screen.CreateSeason = backStackEntry.toRoute()
            CreateSeasonScreen(
                clubId = screen.clubId,
                clubName = screen.clubName,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onSeasonCreated = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(KEY_CLUB_STRUCTURE_CHANGED, true)
                    navController.popBackStack()
                }
            )
        }

        screen<Screen.CreateAgeGroups> { backStackEntry ->
            val screen: Screen.CreateAgeGroups = backStackEntry.toRoute()
            CreateAgeGroupsScreen(
                clubId = screen.clubId,
                clubName = screen.clubName,
                sport = SportType.valueOf(screen.sport),
                initialSeasonId = screen.initialSeasonId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onAgeGroupsCreated = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(KEY_CLUB_STRUCTURE_CHANGED, true)
                    navController.popBackStack()
                }
            )
        }

        screen<Screen.CreateTeams> { backStackEntry ->
            val screen: Screen.CreateTeams = backStackEntry.toRoute()
            CreateTeamsScreen(
                clubId = screen.clubId,
                clubName = screen.clubName,
                sport = SportType.valueOf(screen.sport),
                initialAgeGroupId = screen.initialAgeGroupId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onTeamsCreated = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(KEY_CLUB_STRUCTURE_CHANGED, true)
                    navController.popBackStack()
                }
            )
        }

        screen<Screen.AddPlayersToAgeGroup> { backStackEntry ->
            val screen: Screen.AddPlayersToAgeGroup = backStackEntry.toRoute()
            // Set by CreatePlayers when the "New player" shortcut is used -- reload the eligible list
            // so the just-registered players show up, and pass the signal on down (ClubDetails /
            // AgeGroupDetails) so their player lists refresh too.
            val structureChanged by backStackEntry.savedStateHandle
                .getStateFlow(KEY_CLUB_STRUCTURE_CHANGED, false)
                .collectAsStateWithLifecycle()
            if (structureChanged) {
                navController.consumeAndCascadeStructureChanged(backStackEntry)
            }
            AddPlayersToAgeGroupScreen(
                clubId = screen.clubId,
                clubName = screen.clubName,
                initialAgeGroupId = screen.initialAgeGroupId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToCreatePlayers = { ageGroupId ->
                    navController.navigate(Screen.CreatePlayers(screen.clubId, screen.clubName, ageGroupId))
                },
                onPlayersAdded = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(KEY_CLUB_STRUCTURE_CHANGED, true)
                    navController.popBackStack()
                },
                refreshOnReturn = structureChanged
            )
        }

        screen<Screen.AgeGroupDetails> { backStackEntry ->
            val screen: Screen.AgeGroupDetails = backStackEntry.toRoute()
            val structureChanged by backStackEntry.savedStateHandle
                .getStateFlow(KEY_CLUB_STRUCTURE_CHANGED, false)
                .collectAsStateWithLifecycle()
            val sessionsCreated by backStackEntry.savedStateHandle
                .getStateFlow<Int?>(KEY_TRAINING_MESSAGE, null)
                .collectAsStateWithLifecycle()
            if (structureChanged) {
                navController.consumeAndCascadeStructureChanged(backStackEntry)
            }
            AgeGroupDetailsScreen(
                ageGroupId = screen.ageGroupId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTeam = { teamId -> navController.navigate(Screen.TeamDetails(teamId)) },
                onNavigateToFullStats = { ageGroupId -> navController.navigate(Screen.FullStats(ageGroupId = ageGroupId)) },
                onNavigateToPlayer = { playerId -> navController.navigate(Screen.PlayerDetails(playerId)) },
                onNavigateToMember = { clubId, userId -> navController.navigate(Screen.ClubMemberDetails(clubId, userId = userId)) },
                onNavigateToEditAgeGroup = { ageGroupId -> navController.navigate(Screen.EditAgeGroup(ageGroupId)) },
                onNavigateToMatch = { matchId -> navController.navigate(Screen.MatchDetails(matchId)) },
                onNavigateToAddPlayers = { clubId, clubName, ageGroupId ->
                    navController.navigate(Screen.AddPlayersToAgeGroup(clubId, clubName, ageGroupId))
                },
                onNavigateToAddStaff = { clubId, clubName, ageGroupId ->
                    navController.navigate(Screen.AddStaffToAgeGroup(clubId, clubName, ageGroupId))
                },
                onNavigateToCreateTeams = { clubId, clubName, sport ->
                    navController.navigate(Screen.CreateTeams(clubId, clubName, sport.name))
                },
                onNavigateToCreateMatch = { ageGroupId ->
                    navController.navigate(Screen.CreateMatch(ageGroupId = ageGroupId))
                },
                onNavigateToSeasonPlan = { ageGroupId, edit ->
                    navController.navigate(if (edit) Screen.EditSeasonPlan(ageGroupId) else Screen.SeasonPlan(ageGroupId))
                },
                onNavigateToMonthPlan = { ageGroupId, month, edit ->
                    navController.navigate(if (edit) Screen.EditMonthPlan(ageGroupId, month.toString()) else Screen.MonthPlan(ageGroupId, month.toString()))
                },
                onNavigateToCreateSessions = { ageGroupId -> navController.navigate(Screen.CreateTrainingSessions(ageGroupId)) },
                onNavigateToAddSession = { ageGroupId, date -> navController.navigate(Screen.EditTrainingSession(ageGroupId, date = date?.toString())) },
                onNavigateToSession = { sessionId -> navController.navigate(Screen.TrainingSession(sessionId)) },
                sessionsCreated = sessionsCreated,
                onMessageShown = { backStackEntry.savedStateHandle[KEY_TRAINING_MESSAGE] = null },
                onNavigateToWeekPlan = { ageGroupId, monday, edit ->
                    navController.navigate(if (edit) Screen.EditWeekPlan(ageGroupId, monday.toString()) else Screen.WeekPlan(ageGroupId, monday.toString()))
                },
                refreshOnReturn = structureChanged
            )
        }

        screen<Screen.CreatePlayers> { backStackEntry ->
            val screen: Screen.CreatePlayers = backStackEntry.toRoute()
            CreatePlayersScreen(
                clubId = screen.clubId,
                clubName = screen.clubName,
                ageGroupId = screen.ageGroupId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onPlayersCreated = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(KEY_CLUB_STRUCTURE_CHANGED, true)
                    navController.popBackStack()
                }
            )
        }

        screen<Screen.CreateStaff> { backStackEntry ->
            val screen: Screen.CreateStaff = backStackEntry.toRoute()
            CreateStaffScreen(
                clubId = screen.clubId,
                clubName = screen.clubName,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onStaffCreated = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(KEY_CLUB_STRUCTURE_CHANGED, true)
                    navController.popBackStack()
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
                navController.consumeAndCascadeStructureChanged(backStackEntry)
            }
            AddStaffToAgeGroupScreen(
                clubId = screen.clubId,
                clubName = screen.clubName,
                initialAgeGroupId = screen.initialAgeGroupId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToCreateStaff = {
                    navController.navigate(Screen.CreateStaff(screen.clubId, screen.clubName))
                },
                onStaffAdded = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(KEY_CLUB_STRUCTURE_CHANGED, true)
                    navController.popBackStack()
                },
                refreshOnReturn = structureChanged
            )
        }

        screen<Screen.TeamDetails> { backStackEntry ->
            val screen: Screen.TeamDetails = backStackEntry.toRoute()
            val structureChanged by backStackEntry.savedStateHandle
                .getStateFlow(KEY_CLUB_STRUCTURE_CHANGED, false)
                .collectAsStateWithLifecycle()
            if (structureChanged) {
                navController.consumeAndCascadeStructureChanged(backStackEntry)
            }
            TeamDetailsScreen(
                teamId = screen.teamId,
                onNavigateToMember = { clubId, userId -> navController.navigate(Screen.ClubMemberDetails(clubId, userId = userId)) },
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToAgeGroup = { ageGroupId ->
                    navController.navigate(Screen.AgeGroupDetails(ageGroupId))
                },
                onNavigateToPlayer = { playerId, seasonId, filter ->
                    navController.navigate(Screen.PlayerDetails(playerId, seasonId = seasonId, statsFilter = filter.name))
                },
                onNavigateToMatch = { matchId ->
                    navController.navigate(Screen.MatchDetails(matchId))
                },
                onScheduleMatch = {
                    navController.navigate(Screen.CreateMatch(teamId = screen.teamId))
                },
                onOpenFullStats = { navController.navigate(Screen.FullStats(teamId = screen.teamId)) },
                // The team-editing screen doesn't exist yet -- this affordance stays inert.
                onEditTeam = { navController.navigate(Screen.EditTeam(screen.teamId)) },
                refreshOnReturn = structureChanged
            )
        }

        screen<Screen.FullStats> { backStackEntry ->
            val screen: Screen.FullStats = backStackEntry.toRoute()
            FullStatsScreen(
                teamId = screen.teamId,
                ageGroupId = screen.ageGroupId,
                onNavigateBack = { navController.popBackStack() },
                onOpenPlayer = { playerId, seasonId -> navController.navigate(Screen.PlayerDetails(playerId, seasonId = seasonId)) }
            )
        }

        screen<Screen.CreateMatch> { backStackEntry ->
            val screen: Screen.CreateMatch = backStackEntry.toRoute()
            val pickedOpponentId by backStackEntry.savedStateHandle
                .getStateFlow<String?>(KEY_OPPONENT_PICKED, null)
                .collectAsStateWithLifecycle()
            val createdOpponentName by backStackEntry.savedStateHandle
                .getStateFlow<String?>(KEY_OPPONENT_CREATED, null)
                .collectAsStateWithLifecycle()
            CreateMatchScreen(
                ageGroupId = screen.ageGroupId,
                teamId = screen.teamId,
                onNavigateBack = { navController.popBackStack() },
                onChooseOpponent = { clubId, ageGroupId, selectedOpponentId ->
                    navController.navigate(Screen.Opponents(clubId, select = true, ageGroupId = ageGroupId, selectedOpponentId = selectedOpponentId))
                },
                pickedOpponentId = pickedOpponentId,
                onPickedOpponentConsumed = { backStackEntry.savedStateHandle[KEY_OPPONENT_PICKED] = null },
                createdOpponentName = createdOpponentName,
                onCreatedOpponentShown = { backStackEntry.savedStateHandle[KEY_OPPONENT_CREATED] = null },
                onMatchCreated = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(KEY_CLUB_STRUCTURE_CHANGED, true)
                    navController.popBackStack()
                }
            )
        }

        screen<Screen.Opponents> { backStackEntry ->
            val screen: Screen.Opponents = backStackEntry.toRoute()
            // Set by Create opponent (BROWSE), so the list reloads on return.
            val opponentsChanged by backStackEntry.savedStateHandle
                .getStateFlow(KEY_CLUB_STRUCTURE_CHANGED, false)
                .collectAsStateWithLifecycle()
            if (opponentsChanged) {
                backStackEntry.savedStateHandle[KEY_CLUB_STRUCTURE_CHANGED] = false
            }
            OpponentsScreen(
                clubId = screen.clubId,
                ageGroupId = screen.ageGroupId,
                selectMode = screen.select,
                selectedOpponentId = screen.selectedOpponentId,
                onNavigateBack = { navController.popBackStack() },
                onOpponentClick = { opponent ->
                    if (screen.select) {
                        navController.previousBackStackEntry?.setIfAlive(KEY_OPPONENT_PICKED, opponent.id)
                        navController.popBackStack()
                    } else {
                        navController.navigate(Screen.OpponentDetails(opponent.id))
                    }
                },
                onCreateOpponent = { name ->
                    navController.navigate(Screen.CreateOpponent(screen.clubId, screen.ageGroupId, name, select = screen.select))
                },
                refresh = opponentsChanged
            )
        }

        screen<Screen.OpponentDetails> { backStackEntry ->
            val screen: Screen.OpponentDetails = backStackEntry.toRoute()
            // Set by Edit opponent; passed on so the list underneath reloads too.
            val edited by backStackEntry.savedStateHandle
                .getStateFlow(KEY_CLUB_STRUCTURE_CHANGED, false)
                .collectAsStateWithLifecycle()
            if (edited) {
                navController.consumeAndCascadeStructureChanged(backStackEntry)
            }
            OpponentDetailsScreen(
                opponentId = screen.opponentId,
                onNavigateBack = { navController.popBackStack() },
                onOpenMatch = { matchId -> navController.navigate(Screen.MatchDetails(matchId)) },
                onEdit = { navController.navigate(Screen.EditOpponent(screen.opponentId)) },
                refresh = edited
            )
        }

        screen<Screen.EditOpponent> { backStackEntry ->
            val screen: Screen.EditOpponent = backStackEntry.toRoute()
            OpponentEditScreen(
                opponentId = screen.opponentId,
                onNavigateBack = { navController.popBackStack() },
                onSaved = {
                    navController.previousBackStackEntry?.setIfAlive(KEY_CLUB_STRUCTURE_CHANGED, true)
                    navController.popBackStack()
                }
            )
        }

        screen<Screen.CreateOpponent> { backStackEntry ->
            val screen: Screen.CreateOpponent = backStackEntry.toRoute()
            OpponentFormScreen(
                clubId = screen.clubId,
                ageGroupId = screen.ageGroupId,
                initialName = screen.name,
                onNavigateBack = { navController.popBackStack() },
                onCreated = { opponent ->
                    if (screen.select) {
                        // Picking for a match: skip the list and go straight back to the form, with the
                        // new opponent selected (design 38d).
                        val matchEntry = runCatching { navController.getBackStackEntry<Screen.CreateMatch>() }.getOrNull()
                        matchEntry?.setIfAlive(KEY_OPPONENT_PICKED, opponent.id)
                        matchEntry?.setIfAlive(KEY_OPPONENT_CREATED, opponent.name)
                        if (!navController.popBackStack<Screen.CreateMatch>(inclusive = false)) navController.popBackStack()
                    } else {
                        navController.previousBackStackEntry?.setIfAlive(KEY_CLUB_STRUCTURE_CHANGED, true)
                        navController.popBackStack()
                    }
                }
            )
        }

        screen<Screen.SeasonPlan> { backStackEntry ->
            val screen: Screen.SeasonPlan = backStackEntry.toRoute()
            // Set by the plan editors, so the plan reloads on return.
            val planChanged by backStackEntry.savedStateHandle
                .getStateFlow(KEY_CLUB_STRUCTURE_CHANGED, false)
                .collectAsStateWithLifecycle()
            if (planChanged) navController.consumeAndCascadeStructureChanged(backStackEntry)
            SeasonPlanScreen(
                ageGroupId = screen.ageGroupId,
                onNavigateBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Screen.EditSeasonPlan(screen.ageGroupId)) },
                onOpenMonth = { month -> navController.navigate(Screen.MonthPlan(screen.ageGroupId, month.toString())) },
                refresh = planChanged
            )
        }

        screen<Screen.EditSeasonPlan> { backStackEntry ->
            val screen: Screen.EditSeasonPlan = backStackEntry.toRoute()
            SeasonPlanEditScreen(
                ageGroupId = screen.ageGroupId,
                onNavigateBack = { navController.popBackStack() },
                onSaved = {
                    navController.previousBackStackEntry?.setIfAlive(KEY_CLUB_STRUCTURE_CHANGED, true)
                    navController.popBackStack()
                }
            )
        }

        screen<Screen.MonthPlan> { backStackEntry ->
            val screen: Screen.MonthPlan = backStackEntry.toRoute()
            val planChanged by backStackEntry.savedStateHandle
                .getStateFlow(KEY_CLUB_STRUCTURE_CHANGED, false)
                .collectAsStateWithLifecycle()
            if (planChanged) navController.consumeAndCascadeStructureChanged(backStackEntry)
            MonthPlanScreen(
                ageGroupId = screen.ageGroupId,
                month = LocalDate.parse(screen.month),
                onNavigateBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Screen.EditMonthPlan(screen.ageGroupId, screen.month)) },
                onOpenWeek = { monday -> navController.navigate(Screen.WeekPlan(screen.ageGroupId, monday.toString())) },
                refresh = planChanged
            )
        }

        screen<Screen.EditMonthPlan> { backStackEntry ->
            val screen: Screen.EditMonthPlan = backStackEntry.toRoute()
            PeriodPlanEditScreen(
                ageGroupId = screen.ageGroupId,
                level = TrainingPlanLevel.MONTH,
                periodStart = LocalDate.parse(screen.month),
                onNavigateBack = { navController.popBackStack() },
                onSaved = {
                    navController.previousBackStackEntry?.setIfAlive(KEY_CLUB_STRUCTURE_CHANGED, true)
                    navController.popBackStack()
                }
            )
        }

        screen<Screen.WeekPlan> { backStackEntry ->
            val screen: Screen.WeekPlan = backStackEntry.toRoute()
            val planChanged by backStackEntry.savedStateHandle
                .getStateFlow(KEY_CLUB_STRUCTURE_CHANGED, false)
                .collectAsStateWithLifecycle()
            if (planChanged) navController.consumeAndCascadeStructureChanged(backStackEntry)
            WeekPlanScreen(
                ageGroupId = screen.ageGroupId,
                monday = LocalDate.parse(screen.monday),
                onNavigateBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Screen.EditWeekPlan(screen.ageGroupId, screen.monday)) },
                onOpenSession = { sessionId -> navController.navigate(Screen.TrainingSession(sessionId)) },
                onAddSession = { date -> navController.navigate(Screen.EditTrainingSession(screen.ageGroupId, date = date.toString())) },
                refresh = planChanged
            )
        }

        screen<Screen.EditWeekPlan> { backStackEntry ->
            val screen: Screen.EditWeekPlan = backStackEntry.toRoute()
            PeriodPlanEditScreen(
                ageGroupId = screen.ageGroupId,
                level = TrainingPlanLevel.WEEK,
                periodStart = LocalDate.parse(screen.monday),
                onNavigateBack = { navController.popBackStack() },
                onSaved = {
                    navController.previousBackStackEntry?.setIfAlive(KEY_CLUB_STRUCTURE_CHANGED, true)
                    navController.popBackStack()
                }
            )
        }

        screen<Screen.CreateTrainingSessions> { backStackEntry ->
            val screen: Screen.CreateTrainingSessions = backStackEntry.toRoute()
            CreateSessionsScreen(
                ageGroupId = screen.ageGroupId,
                onNavigateBack = { navController.popBackStack() },
                onCreated = { count ->
                    navController.previousBackStackEntry?.let { previous ->
                        previous.setIfAlive(KEY_CLUB_STRUCTURE_CHANGED, true)
                        previous.setIfAlive(KEY_TRAINING_MESSAGE, count)
                    }
                    navController.popBackStack()
                }
            )
        }

        screen<Screen.TrainingSession> { backStackEntry ->
            val screen: Screen.TrainingSession = backStackEntry.toRoute()
            val sessionChanged by backStackEntry.savedStateHandle
                .getStateFlow(KEY_CLUB_STRUCTURE_CHANGED, false)
                .collectAsStateWithLifecycle()
            if (sessionChanged) navController.consumeAndCascadeStructureChanged(backStackEntry)
            SessionDetailsScreen(
                sessionId = screen.sessionId,
                onNavigateBack = { navController.popBackStack() },
                onEdit = { ageGroupId -> navController.navigate(Screen.EditTrainingSession(ageGroupId, sessionId = screen.sessionId)) },
                onOpenWeekPlan = { ageGroupId, monday, edit ->
                    navController.navigate(if (edit) Screen.EditWeekPlan(ageGroupId, monday.toString()) else Screen.WeekPlan(ageGroupId, monday.toString()))
                },
                onAddExercises = { navController.navigate(Screen.ExerciseLibrary(screen.sessionId)) },
                onRatePlayers = { navController.navigate(Screen.RateTraining(screen.sessionId)) },
                onChanged = { deleted ->
                    navController.previousBackStackEntry?.setIfAlive(KEY_CLUB_STRUCTURE_CHANGED, true)
                    if (deleted) navController.popBackStack()
                },
                refresh = sessionChanged
            )
        }

        screen<Screen.RateTraining> { backStackEntry ->
            val screen: Screen.RateTraining = backStackEntry.toRoute()
            RatePlayersScreen(
                sessionId = screen.sessionId,
                onNavigateBack = { navController.popBackStack() },
                onSaved = {
                    navController.previousBackStackEntry?.setIfAlive(KEY_CLUB_STRUCTURE_CHANGED, true)
                    navController.popBackStack()
                }
            )
        }

        screen<Screen.ExerciseLibrary> { backStackEntry ->
            val screen: Screen.ExerciseLibrary = backStackEntry.toRoute()
            ExerciseLibraryScreen(
                sessionId = screen.sessionId,
                onNavigateBack = { navController.popBackStack() },
                onAdded = {
                    navController.previousBackStackEntry?.setIfAlive(KEY_CLUB_STRUCTURE_CHANGED, true)
                    navController.popBackStack()
                }
            )
        }

        screen<Screen.EditTrainingSession> { backStackEntry ->
            val screen: Screen.EditTrainingSession = backStackEntry.toRoute()
            SessionEditScreen(
                ageGroupId = screen.ageGroupId,
                sessionId = screen.sessionId,
                date = screen.date?.let { LocalDate.parse(it) },
                onNavigateBack = { navController.popBackStack() },
                onSaved = {
                    navController.previousBackStackEntry?.setIfAlive(KEY_CLUB_STRUCTURE_CHANGED, true)
                    navController.popBackStack()
                }
            )
        }

        screen<Screen.MatchDetails> { backStackEntry ->
            val screen: Screen.MatchDetails = backStackEntry.toRoute()
            val structureChanged by backStackEntry.savedStateHandle
                .getStateFlow(KEY_CLUB_STRUCTURE_CHANGED, false)
                .collectAsStateWithLifecycle()
            if (structureChanged) {
                navController.consumeAndCascadeStructureChanged(backStackEntry)
            }
            MatchDetailsScreen(
                matchId = screen.matchId,
                onNavigateBack = { navController.popBackStack() },
                onEnterLiveMatch = { matchId -> navController.navigate(Screen.LiveMatch(matchId)) },
                onOpenReport = { matchId, edit -> navController.navigate(Screen.MatchReport(matchId, edit)) },
                onOpenRecord = { matchId -> navController.navigate(Screen.MatchRecord(matchId)) },
                onRedirectToLiveMatch = { matchId ->
                    // Replace this entry rather than push on top of it -- otherwise back from
                    // LiveMatch lands back on this screen, which immediately redirects again.
                    navController.navigate(Screen.LiveMatch(matchId)) {
                        popUpTo(screen) { inclusive = true }
                    }
                },
                refreshOnReturn = structureChanged
            )
        }

        screen<Screen.LiveMatch> { backStackEntry ->
            val screen: Screen.LiveMatch = backStackEntry.toRoute()
            // Captured once, while this entry is on top -- previousBackStackEntry at this point is
            // MatchDetails' entry. Flagging it in onDispose (rather than only from onNavigateBack)
            // covers every way this screen can be left, including the system/gesture back button,
            // which pops the stack directly without ever calling onNavigateBack.
            val matchDetailsEntry = navController.previousBackStackEntry
            DisposableEffect(Unit) {
                onDispose {
                    // Every action here (kick off, half/full time, events) mutates the match, and
                    // there's no separate "nothing changed" exit -- always flag it so MatchDetails
                    // (and, cascading further, the screen that opened it) reload instead of showing a
                    // stale status/score, e.g. the "enter live match" button after full time.
                    // On an activity relaunch the entry is already destroyed here -- see setIfAlive.
                    matchDetailsEntry?.setIfAlive(KEY_CLUB_STRUCTURE_CHANGED, true)
                }
            }
            LiveMatchScreen(
                matchId = screen.matchId,
                onNavigateBack = { navController.popBackStack() },
                onOpenReport = { matchId, edit -> navController.navigate(Screen.MatchReport(matchId, edit)) }
            )
        }

        screen<Screen.MatchReport> { backStackEntry ->
            val screen: Screen.MatchReport = backStackEntry.toRoute()
            // Captured while this entry is on top: the screen that opened the report (Match Details).
            // Flagged as soon as a save succeeds rather than only from onNavigateBack, because the
            // system/gesture back button pops this entry directly without calling it -- the same reason
            // LiveMatch flags its caller in onDispose. Match Details then reloads and cascades to Home.
            val openerEntry = remember { navController.previousBackStackEntry }
            MatchReportScreen(
                matchId = screen.matchId,
                startEditing = screen.edit,
                onNavigateBack = { navController.popBackStack() },
                onReportSaved = { openerEntry?.setIfAlive(KEY_CLUB_STRUCTURE_CHANGED, true) }
            )
        }

        screen<Screen.MatchRecord> { backStackEntry ->
            val screen: Screen.MatchRecord = backStackEntry.toRoute()
            // Saved: back to Match Details, which reloads (and cascades to the lists that show the score).
            MatchRecordScreen(
                matchId = screen.matchId,
                onNavigateBack = { navController.popBackStack() },
                onSaved = {
                    navController.previousBackStackEntry?.setIfAlive(KEY_CLUB_STRUCTURE_CHANGED, true)
                    navController.popBackStack()
                }
            )
        }

        screen<Screen.PlayerDetails> { backStackEntry ->
            val screen: Screen.PlayerDetails = backStackEntry.toRoute()
            val structureChanged by backStackEntry.savedStateHandle
                .getStateFlow(KEY_CLUB_STRUCTURE_CHANGED, false)
                .collectAsStateWithLifecycle()
            if (structureChanged) {
                navController.consumeAndCascadeStructureChanged(backStackEntry)
            }
            PlayerDetailsScreen(
                playerId = screen.playerId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onEditPlayer = { playerId ->
                    navController.navigate(Screen.EditPlayer(playerId))
                },
                onOpenMatch = { matchId ->
                    navController.navigate(Screen.MatchDetails(matchId))
                },
                initialSeasonId = screen.seasonId,
                initialFilter = screen.statsFilter?.let { name -> StatsFilter.entries.firstOrNull { it.name == name } },
                refreshOnReturn = structureChanged
            )
        }

        screen<Screen.EditPlayer> { backStackEntry ->
            val screen: Screen.EditPlayer = backStackEntry.toRoute()
            EditPlayerScreen(
                playerId = screen.playerId,
                onNavigateBack = { navController.popBackStack() },
                onPlayerUpdated = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(KEY_CLUB_STRUCTURE_CHANGED, true)
                    navController.popBackStack()
                },
                // Saved straight away: the screen below reloads when it's back on top.
                onPhotoChanged = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(KEY_CLUB_STRUCTURE_CHANGED, true)
                }
            )
        }

        screen<Screen.Account> { entry ->
            val changedEmail by entry.savedStateHandle.getStateFlow<String?>(KEY_EMAIL_CHANGED, null).collectAsStateWithLifecycle()
            AccountScreen(
                changedEmail = changedEmail,
                onNavigateToChangeEmail = { email -> navController.navigate(Screen.ChangeEmail(email)) },
                onNavigateBack = {
                    navController.popBackStack()
                },
                // Clear the whole back stack: a single-club user has no My clubs underneath, and a
                // club left under Sign in would also draw it in that club's colours.
                onNavigateToLogin = {
                    navController.navigate(Screen.Login) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                }
            )
        }
    }
}
