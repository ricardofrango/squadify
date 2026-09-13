package com.ricardofrango.squadify.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.ricardofrango.squadify.presentation.ui.login.LoginScreen
import com.ricardofrango.squadify.presentation.ui.register.RegisterScreen
import com.ricardofrango.squadify.presentation.ui.splash.SplashScreen
import com.ricardofrango.squadify.presentation.ui.dashboard.DashboardScreen
import com.ricardofrango.squadify.presentation.ui.account.AccountScreen
import com.ricardofrango.squadify.presentation.ui.clubdetails.ClubDetailsScreen
import com.ricardofrango.squadify.presentation.ui.createseason.CreateSeasonScreen
import com.ricardofrango.squadify.presentation.ui.createteams.CreateTeamsScreen
import com.ricardofrango.squadify.presentation.ui.addplayerstoagegroup.AddPlayersToAgeGroupScreen
import com.ricardofrango.squadify.presentation.ui.createplayers.CreatePlayersScreen
import com.ricardofrango.squadify.presentation.ui.addstafftoagegroup.AddStaffToAgeGroupScreen
import com.ricardofrango.squadify.presentation.ui.createstaff.CreateStaffScreen
import com.ricardofrango.squadify.presentation.ui.agegroupdetails.AgeGroupDetailsScreen
import com.ricardofrango.squadify.presentation.ui.createagegroups.CreateAgeGroupsScreen
import com.ricardofrango.squadify.presentation.ui.teamdetails.TeamDetailsScreen
import com.ricardofrango.squadify.presentation.ui.creatematch.CreateMatchScreen
import com.ricardofrango.squadify.presentation.ui.matchdetails.MatchDetailsScreen
import com.ricardofrango.squadify.presentation.ui.livematch.LiveMatchScreen
import com.ricardofrango.squadify.presentation.ui.playerdetails.PlayerDetailsScreen
import com.ricardofrango.squadify.presentation.ui.editplayer.EditPlayerScreen

// Key used on a back-stack entry's SavedStateHandle to signal "a season, age group, team, player, or
// match was just created/changed, force a reload" -- the standard Compose Navigation pattern for a
// screen to return a result to the screen that pushed it. See ClubDetailsScreen's `refreshClubDetails`
// doc.
private const val KEY_CLUB_STRUCTURE_CHANGED = "clubStructureChanged"

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
fun AppNavigation() {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = Screen.Splash
    ) {
        composable<Screen.Splash> {
            SplashScreen(
                onNavigateToLogin = {
                    navController.navigate(Screen.Login) {
                        popUpTo(Screen.Splash) { inclusive = true }
                    }
                },
                onNavigateToDashboard = {
                    navController.navigate(Screen.Dashboard) {
                        popUpTo(Screen.Splash) { inclusive = true }
                    }
                }
            )
        }

        composable<Screen.Login> {
            LoginScreen(
                onNavigateToRegister = {
                    navController.navigate(Screen.Register)
                },
                onNavigateToDashboard = {
                    navController.navigate(Screen.Dashboard) {
                        popUpTo(Screen.Login) { inclusive = true }
                    }
                }
            )
        }
        
        composable<Screen.Register> {
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
        
        composable<Screen.Dashboard> {
            DashboardScreen(
                onNavigateToClub = { clubId ->
                    navController.navigate(Screen.ClubDetails(clubId))
                },
                onNavigateToAccount = {
                    navController.navigate(Screen.Account)
                }
            )
        }

        composable<Screen.ClubDetails> { backStackEntry ->
            val screen: Screen.ClubDetails = backStackEntry.toRoute()
            val structureChanged by backStackEntry.savedStateHandle
                .getStateFlow(KEY_CLUB_STRUCTURE_CHANGED, false)
                .collectAsState()
            if (structureChanged) {
                // Consume the flag so it doesn't keep forcing reloads on every recomposition.
                backStackEntry.savedStateHandle[KEY_CLUB_STRUCTURE_CHANGED] = false
            }
            ClubDetailsScreen(
                clubId = screen.clubId,
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
                        Screen.CreateAgeGroups(screen.clubId, clubName, sport, initialSeasonId)
                    )
                },
                onNavigateToCreateTeams = { clubName, sport ->
                    navController.navigate(Screen.CreateTeams(screen.clubId, clubName, sport))
                },
                onNavigateToAddPlayersToAgeGroup = { clubName ->
                    navController.navigate(Screen.AddPlayersToAgeGroup(screen.clubId, clubName))
                },
                onNavigateToCreatePlayers = { clubName ->
                    navController.navigate(Screen.CreatePlayers(screen.clubId, clubName))
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
                refreshClubDetails = structureChanged
            )
        }

        composable<Screen.CreateSeason> { backStackEntry ->
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

        composable<Screen.CreateAgeGroups> { backStackEntry ->
            val screen: Screen.CreateAgeGroups = backStackEntry.toRoute()
            CreateAgeGroupsScreen(
                clubId = screen.clubId,
                clubName = screen.clubName,
                sport = screen.sport,
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

        composable<Screen.CreateTeams> { backStackEntry ->
            val screen: Screen.CreateTeams = backStackEntry.toRoute()
            CreateTeamsScreen(
                clubId = screen.clubId,
                clubName = screen.clubName,
                sport = screen.sport,
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

        composable<Screen.AddPlayersToAgeGroup> { backStackEntry ->
            val screen: Screen.AddPlayersToAgeGroup = backStackEntry.toRoute()
            // Set by CreatePlayers when the "New player" shortcut is used -- reload the eligible list
            // so the just-registered players show up, and pass the signal on down (ClubDetails /
            // AgeGroupDetails) so their player lists refresh too.
            val structureChanged by backStackEntry.savedStateHandle
                .getStateFlow(KEY_CLUB_STRUCTURE_CHANGED, false)
                .collectAsState()
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
                onNavigateToCreatePlayers = {
                    navController.navigate(Screen.CreatePlayers(screen.clubId, screen.clubName))
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

        composable<Screen.AgeGroupDetails> { backStackEntry ->
            val screen: Screen.AgeGroupDetails = backStackEntry.toRoute()
            val structureChanged by backStackEntry.savedStateHandle
                .getStateFlow(KEY_CLUB_STRUCTURE_CHANGED, false)
                .collectAsState()
            if (structureChanged) {
                navController.consumeAndCascadeStructureChanged(backStackEntry)
            }
            AgeGroupDetailsScreen(
                ageGroupId = screen.ageGroupId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTeam = { teamId -> navController.navigate(Screen.TeamDetails(teamId)) },
                onNavigateToPlayer = { playerId -> navController.navigate(Screen.PlayerDetails(playerId)) },
                onNavigateToMatch = { matchId -> navController.navigate(Screen.MatchDetails(matchId)) },
                onNavigateToAddPlayers = { clubId, clubName, ageGroupId ->
                    navController.navigate(Screen.AddPlayersToAgeGroup(clubId, clubName, ageGroupId))
                },
                onNavigateToAddStaff = { clubId, clubName, ageGroupId ->
                    navController.navigate(Screen.AddStaffToAgeGroup(clubId, clubName, ageGroupId))
                },
                onNavigateToCreateTeams = { clubId, clubName, sport ->
                    navController.navigate(Screen.CreateTeams(clubId, clubName, sport))
                },
                onNavigateToCreateMatch = { ageGroupId ->
                    navController.navigate(Screen.CreateMatch(ageGroupId = ageGroupId))
                },
                refreshOnReturn = structureChanged
            )
        }

        composable<Screen.CreatePlayers> { backStackEntry ->
            val screen: Screen.CreatePlayers = backStackEntry.toRoute()
            CreatePlayersScreen(
                clubId = screen.clubId,
                clubName = screen.clubName,
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

        composable<Screen.CreateStaff> { backStackEntry ->
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

        composable<Screen.AddStaffToAgeGroup> { backStackEntry ->
            val screen: Screen.AddStaffToAgeGroup = backStackEntry.toRoute()
            // Set by CreateStaff when the "New staff member" shortcut is used -- reload the eligible
            // list so the just-registered members show up, and pass the signal on down (ClubDetails /
            // AgeGroupDetails) so their state refreshes too. Mirrors AddPlayersToAgeGroup.
            val structureChanged by backStackEntry.savedStateHandle
                .getStateFlow(KEY_CLUB_STRUCTURE_CHANGED, false)
                .collectAsState()
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

        composable<Screen.TeamDetails> { backStackEntry ->
            val screen: Screen.TeamDetails = backStackEntry.toRoute()
            val structureChanged by backStackEntry.savedStateHandle
                .getStateFlow(KEY_CLUB_STRUCTURE_CHANGED, false)
                .collectAsState()
            if (structureChanged) {
                navController.consumeAndCascadeStructureChanged(backStackEntry)
            }
            TeamDetailsScreen(
                teamId = screen.teamId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToAgeGroup = { ageGroupId ->
                    navController.navigate(Screen.AgeGroupDetails(ageGroupId))
                },
                onNavigateToPlayer = { playerId ->
                    navController.navigate(Screen.PlayerDetails(playerId))
                },
                onNavigateToMatch = { matchId ->
                    navController.navigate(Screen.MatchDetails(matchId))
                },
                onScheduleMatch = {
                    navController.navigate(Screen.CreateMatch(teamId = screen.teamId))
                },
                // The team-editing screen doesn't exist yet -- this affordance stays inert.
                onEditTeam = {},
                refreshOnReturn = structureChanged
            )
        }

        composable<Screen.CreateMatch> { backStackEntry ->
            val screen: Screen.CreateMatch = backStackEntry.toRoute()
            CreateMatchScreen(
                ageGroupId = screen.ageGroupId,
                teamId = screen.teamId,
                onNavigateBack = { navController.popBackStack() },
                onMatchCreated = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(KEY_CLUB_STRUCTURE_CHANGED, true)
                    navController.popBackStack()
                }
            )
        }

        composable<Screen.MatchDetails> { backStackEntry ->
            val screen: Screen.MatchDetails = backStackEntry.toRoute()
            val structureChanged by backStackEntry.savedStateHandle
                .getStateFlow(KEY_CLUB_STRUCTURE_CHANGED, false)
                .collectAsState()
            if (structureChanged) {
                navController.consumeAndCascadeStructureChanged(backStackEntry)
            }
            MatchDetailsScreen(
                matchId = screen.matchId,
                onNavigateBack = { navController.popBackStack() },
                onEnterLiveMatch = { matchId -> navController.navigate(Screen.LiveMatch(matchId)) },
                refreshOnReturn = structureChanged
            )
        }

        composable<Screen.LiveMatch> { backStackEntry ->
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
                    matchDetailsEntry?.savedStateHandle?.set(KEY_CLUB_STRUCTURE_CHANGED, true)
                }
            }
            LiveMatchScreen(
                matchId = screen.matchId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<Screen.PlayerDetails> { backStackEntry ->
            val screen: Screen.PlayerDetails = backStackEntry.toRoute()
            val structureChanged by backStackEntry.savedStateHandle
                .getStateFlow(KEY_CLUB_STRUCTURE_CHANGED, false)
                .collectAsState()
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
                refreshOnReturn = structureChanged
            )
        }

        composable<Screen.EditPlayer> { backStackEntry ->
            val screen: Screen.EditPlayer = backStackEntry.toRoute()
            EditPlayerScreen(
                playerId = screen.playerId,
                onNavigateBack = { navController.popBackStack() },
                onPlayerUpdated = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(KEY_CLUB_STRUCTURE_CHANGED, true)
                    navController.popBackStack()
                }
            )
        }

        composable<Screen.Account> {
            AccountScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToLogin = {
                    navController.navigate(Screen.Login) {
                        popUpTo(Screen.Dashboard) { inclusive = true }
                    }
                }
            )
        }
    }
}
