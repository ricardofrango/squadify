package com.ricardofrango.footballmanager.presentation.navigation

import androidx.compose.runtime.Composable
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
            ClubDetailsScreen(
                clubId = screen.clubId,
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
