package com.ricardofrango.clubmanager.presentation.navigation

import com.ricardofrango.clubmanager.model.SportType
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
        // The club's sport -- feeds the team-size picker's options (`sport.teamSizes`).
        val sport: SportType,
        // Optional pre-selection for the screen's own season picker: set when the caller already knows
        // which season is meant (a specific season's "no age groups" empty state), null from the
        // ambiguous overflow-menu entry so the user has to pick one explicitly. The screen loads the
        // club's seasons itself and resolves the name / end year (for AgeGroupDefaults.inferredName)
        // from the picked one.
        val initialSeasonId: String? = null
    ) : Screen()

    @Serializable
    data class CreateTeams(val clubId: String, val clubName: String, val sport: SportType) : Screen()

    // The screen has its own age-group picker; [initialAgeGroupId] just pre-selects it when the caller
    // already knows which one (e.g. from the age-group detail screen). [clubName] is carried only so
    // the "New player" shortcut can push CreatePlayers, which shows it in its header.
    @Serializable
    data class AddPlayersToAgeGroup(
        val clubId: String,
        val clubName: String,
        val initialAgeGroupId: String? = null
    ) : Screen()

    @Serializable
    data class CreatePlayers(val clubId: String, val clubName: String) : Screen()

    // Registers a brand-new person to the club (by email -- see AddClubMembersRequest): an existing
    // account is added as a member immediately, an unknown email gets an invite. This is how a
    // not-yet-a-member coach/staff gets into the club in the first place; AddStaffToAgeGroup then
    // assigns an existing member to a specific age group.
    @Serializable
    data class CreateStaff(val clubId: String, val clubName: String) : Screen()

    // Assigns an existing club member as an age group's coach/staff. The screen has its own
    // age-group picker; [initialAgeGroupId] just pre-selects it, same as AddPlayersToAgeGroup.
    @Serializable
    data class AddStaffToAgeGroup(
        val clubId: String,
        val clubName: String,
        val initialAgeGroupId: String? = null
    ) : Screen()

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

    // The live match console: pre-kickoff line-up, live squad/events, full-time summary.
    @Serializable
    data class LiveMatch(val matchId: String) : Screen()

    @Serializable
    data class PlayerDetails(val playerId: String) : Screen()

    @Serializable
    data class EditPlayer(val playerId: String) : Screen()
}
