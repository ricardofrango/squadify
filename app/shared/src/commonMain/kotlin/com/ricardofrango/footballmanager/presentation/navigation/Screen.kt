package com.ricardofrango.footballmanager.presentation.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed class Screen {
    @Serializable
    data object Splash : Screen()

    @Serializable
    data object Login : Screen()

    @Serializable
    data object Register : Screen()

    @Serializable
    data object Dashboard : Screen()
    
    @Serializable
    data object Account : Screen()
}
