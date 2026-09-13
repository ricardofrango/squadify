package com.ricardofrango.squadify

import androidx.compose.runtime.Composable
import com.ricardofrango.squadify.presentation.navigation.AppNavigation
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme

@Composable
fun App() {
    SquadifyTheme {
        AppNavigation()
    }
}
