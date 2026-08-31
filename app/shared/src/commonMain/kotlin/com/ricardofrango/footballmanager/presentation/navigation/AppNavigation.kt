package com.ricardofrango.footballmanager.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ricardofrango.footballmanager.presentation.ui.login.LoginScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = Screen.Login
    ) {
        composable<Screen.Login> {
            LoginScreen(
                onNavigateToRegister = {
                    navController.navigate(Screen.Register)
                }
            )
        }
        
        composable<Screen.Register> {
            // RegisterScreen implementation later
        }
        
        composable<Screen.Dashboard> {
            // DashboardScreen implementation later
        }
    }
}
