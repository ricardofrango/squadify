package com.ricardofrango.clubmanager

import androidx.compose.runtime.Composable
import com.ricardofrango.clubmanager.presentation.navigation.AppNavigation
import com.ricardofrango.clubmanager.presentation.ui.theme.ClubManagerTheme

@Composable
fun App() {
    ClubManagerTheme {
        AppNavigation()
    }
}
