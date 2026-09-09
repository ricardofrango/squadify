package com.ricardofrango.clubmanager.presentation.navigation

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
        // club's seasons itself and resolves the name / end year (for AgeGroupDefaults.inferredName)
        // from the picked one.
        val initialSeasonId: String? = null
    ) : Screen()

    @Serializable
    data class CreateTeams(val clubId: String, val clubName: String) : Screen()

    // The screen has its own age-group picker; [initialAgeGroupId] just pre-selects it when the caller
    // already knows which one (e.g. from the age-group detail screen).
    @Serializable
    data class AddPlayersToAgeGroup(val clubId: String, val initialAgeGroupId: String? = null) : Screen()

    @Serializable
    data class CreatePlayers(val clubId: String, val clubName: String) : Screen()

    @Serializable
    data class AgeGroupDetails(val ageGroupId: String) : Screen()

    @Serializable
    data class TeamDetails(val teamId: String) : Screen()

    // Schedule one fixture. Exactly one of [ageGroupId] / [teamId] is set: from an age group the
    // team is picked on the screen; from a team it's fixed. The screen loads the club / season /
    // teams context from whichever id it's given.
    @Serializable
    data class CreateMatch(
        val ageGroupId: String? = null,
        val teamId: String? = null
    ) : Screen()

    @Serializable
    data class MatchDetails(val matchId: String) : Screen()

    // The live / in-progress match screen (start, substitutions, goals, cards). Placeholder for now.
    @Serializable
    data class LiveMatch(val matchId: String) : Screen()

    @Serializable
    data class PlayerDetails(val playerId: String) : Screen()
}
