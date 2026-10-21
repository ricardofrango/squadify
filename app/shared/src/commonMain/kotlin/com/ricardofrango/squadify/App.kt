package com.ricardofrango.squadify

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.rememberNavController
import com.ricardofrango.squadify.presentation.navigation.AppNavigation
import com.ricardofrango.squadify.presentation.navigation.LocalAppNavController
import com.ricardofrango.squadify.presentation.navigation.LocalClubThemes
import com.ricardofrango.squadify.presentation.ui.theme.ClubThemeStore
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import org.koin.compose.koinInject

@Composable
fun App() {
    SquadifyTheme {
        // Each screen is drawn in the colours of the club it was opened under (ClubThemedEntry).
        val navController = rememberNavController()
        val clubThemes by koinInject<ClubThemeStore>().themes.collectAsState()
        CompositionLocalProvider(LocalAppNavController provides navController, LocalClubThemes provides clubThemes) {
            AppNavigation(navController)
        }
    }
}
