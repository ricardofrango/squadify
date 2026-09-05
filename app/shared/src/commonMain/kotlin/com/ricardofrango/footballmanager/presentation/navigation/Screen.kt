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

    @Serializable
    data class ClubDetails(val clubId: String) : Screen()

    @Serializable
    data class CreateSeason(val clubId: String, val clubName: String) : Screen()

    @Serializable
    data class CreateAgeGroups(
        val clubId: String,
        val clubName: String,
        // Optional pre-selection for the screen's own season picker: set when the caller already knows
        // which season is meant (a specific season's "no age groups" empty state), null from the
        // ambiguous overflow-menu entry so the user has to pick one explicitly. The screen loads the
        // club's seasons itself and resolves the name / start year (for AgeGroupDefaults.inferredName)
        // from the picked one.
        val initialSeasonId: String? = null
    ) : Screen()

    @Serializable
    data class CreateTeams(val clubId: String, val clubName: String) : Screen()

    @Serializable
    data class TeamDetails(val teamId: String) : Screen()

    @Serializable
    data class PlayerDetails(val playerId: String) : Screen()
}
