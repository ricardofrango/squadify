package com.ricardofrango.footballmanager.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.ricardofrango.footballmanager.presentation.ui.login.LoginScreen
import com.ricardofrango.footballmanager.presentation.ui.register.RegisterScreen
import com.ricardofrango.footballmanager.presentation.ui.splash.SplashScreen
import com.ricardofrango.footballmanager.presentation.ui.dashboard.DashboardScreen
import com.ricardofrango.footballmanager.presentation.ui.account.AccountScreen
import com.ricardofrango.footballmanager.presentation.ui.clubdetails.ClubDetailsScreen
import com.ricardofrango.footballmanager.presentation.ui.createseason.CreateSeasonScreen
import com.ricardofrango.footballmanager.presentation.ui.teamdetails.TeamDetailsScreen
import com.ricardofrango.footballmanager.presentation.ui.playerdetails.PlayerDetailsScreen

// Key used on the ClubDetails back-stack entry's SavedStateHandle to signal "a season was just
// created, force a reload" -- the standard Compose Navigation pattern for a screen to return a
// result to the screen that pushed it. See ClubDetailsScreen's `refreshAfterSeasonCreated` doc.
private const val KEY_SEASON_CREATED = "seasonCreated"

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
            val seasonCreated by backStackEntry.savedStateHandle
                .getStateFlow(KEY_SEASON_CREATED, false)
                .collectAsState()
            if (seasonCreated) {
                // Consume the flag so it doesn't keep forcing reloads on every recomposition.
                backStackEntry.savedStateHandle[KEY_SEASON_CREATED] = false
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
                refreshAfterSeasonCreated = seasonCreated
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
                        ?.set(KEY_SEASON_CREATED, true)
                    navController.popBackStack()
                }
            )
        }

        composable<Screen.TeamDetails> { backStackEntry ->
            val screen: Screen.TeamDetails = backStackEntry.toRoute()
            TeamDetailsScreen(
                teamId = screen.teamId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToPlayer = { playerId ->
                    navController.navigate(Screen.PlayerDetails(playerId))
                }
            )
        }

        composable<Screen.PlayerDetails> { backStackEntry ->
            val screen: Screen.PlayerDetails = backStackEntry.toRoute()
            PlayerDetailsScreen(
                playerId = screen.playerId,
                onNavigateBack = {
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
