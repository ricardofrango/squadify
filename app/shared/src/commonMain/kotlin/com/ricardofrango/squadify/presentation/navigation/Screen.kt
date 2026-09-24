package com.ricardofrango.squadify.presentation.navigation

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

    // isRootClub is true only when this is the single club a user with just one club is bounced to
    // straight from Dashboard (see DashboardViewModel.NavigateToOnlyClub) -- Dashboard is popped off
    // the back stack in that case, so ClubDetailsScreen hides its back button and centers the title
    // instead of pointing at a clubs list the user can no longer reach.
    @Serializable
    data class ClubDetails(val clubId: String, val isRootClub: Boolean = false) : Screen()

    @Serializable
    data class CreateSeason(val clubId: String, val clubName: String) : Screen()

    // Tabbed "Club Details" / "Permissions" (the latter empty for now -- see TECHNICAL_DECISIONS.md).
    // The screen loads the club itself from [clubId], so it needs nothing else from the caller.
    @Serializable
    data class EditClub(val clubId: String) : Screen()

    @Serializable
    data class CreateAgeGroups(
        val clubId: String,
        val clubName: String,
        // SportType's .name -- Navigation-Compose's type-safe routes have no built-in NavType for enum
        // arguments (this crashed the whole app graph at startup: "could not find any NavType for
        // argument sport ... typeMap received was {}"), so it travels as a String and is converted back
        // with SportType.valueOf() at the composable<Screen.CreateAgeGroups> destination.
        val sport: String,
        // Optional pre-selection for the screen's own season picker: set when the caller already knows
        // which season is meant (a specific season's "no age groups" empty state), null from the
        // ambiguous overflow-menu entry so the user has to pick one explicitly. The screen loads the
        // club's seasons itself and resolves the name / end year (for AgeGroupDefaults.inferredName)
        // from the picked one.
        val initialSeasonId: String? = null
    ) : Screen()

    // sport is SportType's .name -- see CreateAgeGroups' doc above for why this isn't the enum itself.
    @Serializable
    data class CreateTeams(val clubId: String, val clubName: String, val sport: String) : Screen()

    // The screen has its own age-group picker; [initialAgeGroupId] just pre-selects it when the caller
    // already knows which one (e.g. from the age-group detail screen). [clubName] is carried only so
    // the "New player" shortcut can push CreatePlayers, which shows it in its header.
    @Serializable
    data class AddPlayersToAgeGroup(
        val clubId: String,
        val clubName: String,
        val initialAgeGroupId: String? = null
    ) : Screen()

    // ageGroupId: set when reached from an age group's own "Add players" screen ("+ Add new player"),
    // so the coach of specifically that age group -- not just a club admin/coordinator -- is
    // authorized to create these players server-side. Null from the standalone club-roster entry
    // points (Club Details' "Add players to club"), which stay coordinator-only.
    @Serializable
    data class CreatePlayers(val clubId: String, val clubName: String, val ageGroupId: String? = null) : Screen()

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
