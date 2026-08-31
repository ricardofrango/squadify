package com.ricardofrango.footballmanager

import androidx.compose.runtime.Composable
import com.ricardofrango.footballmanager.presentation.navigation.AppNavigation
import com.ricardofrango.footballmanager.presentation.ui.theme.FootballManagerTheme

@Composable
fun App() {
    FootballManagerTheme {
        AppNavigation()
    }
}
