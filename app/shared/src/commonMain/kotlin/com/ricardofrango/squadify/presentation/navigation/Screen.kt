package com.ricardofrango.squadify.presentation.navigation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Every entry below carries an explicit @SerialName: androidx.navigation's type-safe routes derive each
// screen's URL path from KSerializer.descriptor.serialName, which defaults to the class's fully-qualified
// name (e.g. "com.ricardofrango.squadify.presentation.navigation.Screen.ClubDetails") when unset -- fine
// for Android/iOS/Desktop, where the route string is never user-visible, but the web target's browser
// address bar shows it verbatim (see WebDeepLinking.kt/TECHNICAL_DECISIONS.md). These names are also the
// deep-link contract: they end up in shared URLs and pasted into the address bar, so treat renaming one as
// a breaking change to any link already shared, not just a cosmetic rename.
@Serializable
sealed class Screen {
    @Serializable
    @SerialName("splash")
    data object Splash : Screen()

    @Serializable
    @SerialName("login")
    data object Login : Screen()

    @Serializable
    @SerialName("register")
    data object Register : Screen()

    @Serializable
    @SerialName("dashboard")
    data object Dashboard : Screen()

    @Serializable
    @SerialName("account")
    data object Account : Screen()

    // isRootClub is true only when this is the single club a user with just one club is bounced to
    // straight from Dashboard (see DashboardViewModel.NavigateToOnlyClub) -- Dashboard is popped off
    // the back stack in that case, so ClubDetailsScreen hides its back button and centers the title
    // instead of pointing at a clubs list the user can no longer reach.
    @Serializable
    @SerialName("club")
    data class ClubDetails(val clubId: String, val isRootClub: Boolean = false) : Screen()

    @Serializable
    @SerialName("create-season")
    data class CreateSeason(val clubId: String, val clubName: String) : Screen()

    // Tabbed "Club Details" / "Permissions" (the latter empty for now -- see TECHNICAL_DECISIONS.md).
    // The screen loads the club itself from [clubId], so it needs nothing else from the caller.
    // The club's player list (from the More panel) -- its own screen, without Club details' bottom bar.
    @Serializable
    @SerialName("club-players")
    data class ClubPlayers(val clubId: String) : Screen()

    @Serializable
    @SerialName("edit-club")
    data class EditClub(val clubId: String) : Screen()

    // One club member (design 35), or one pending club invite: exactly one of [userId] / [inviteId] is set.
    @Serializable
    @SerialName("club-member")
    data class ClubMemberDetails(val clubId: String, val userId: String? = null, val inviteId: String? = null) : Screen()

    @Serializable
    @SerialName("create-age-groups")
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
    @SerialName("create-teams")
    // [initialAgeGroupId] preselects the age group (and its season) -- a coach's "Create team" from the
    // More panel is scoped to the age group they picked.
    data class CreateTeams(val clubId: String, val clubName: String, val sport: String, val initialAgeGroupId: String? = null) : Screen()

    // The screen has its own age-group picker; [initialAgeGroupId] just pre-selects it when the caller
    // already knows which one (e.g. from the age-group detail screen). [clubName] is carried only so
    // the "New player" shortcut can push CreatePlayers, which shows it in its header.
    @Serializable
    @SerialName("add-players-to-age-group")
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
    @SerialName("create-players")
    data class CreatePlayers(val clubId: String, val clubName: String, val ageGroupId: String? = null) : Screen()

    // Registers a brand-new person to the club (by email -- see AddClubMembersRequest): an existing
    // account is added as a member immediately, an unknown email gets an invite. This is how a
    // not-yet-a-member coach/staff gets into the club in the first place; AddStaffToAgeGroup then
    // assigns an existing member to a specific age group.
    @Serializable
    @SerialName("create-staff")
    data class CreateStaff(val clubId: String, val clubName: String) : Screen()

    // Assigns an existing club member as an age group's coach/staff. The screen has its own
    // age-group picker; [initialAgeGroupId] just pre-selects it, same as AddPlayersToAgeGroup.
    @Serializable
    @SerialName("add-staff-to-age-group")
    data class AddStaffToAgeGroup(
        val clubId: String,
        val clubName: String,
        val initialAgeGroupId: String? = null
    ) : Screen()

    @Serializable
    @SerialName("age-group")
    data class AgeGroupDetails(val ageGroupId: String) : Screen()

    @Serializable
    @SerialName("team")
    data class TeamDetails(val teamId: String) : Screen()

    @Serializable
    @SerialName("edit-age-group")
    data class EditAgeGroup(val ageGroupId: String) : Screen()

    @Serializable
    @SerialName("edit-team")
    data class EditTeam(val teamId: String) : Screen()

    // Schedule one fixture. Exactly one of [ageGroupId] / [teamId] is set: from an age group the
    // team is picked on the screen; from a team it's fixed. The screen loads the club / season /
    // teams context from whichever id it's given.
    @Serializable
    @SerialName("create-match")
    data class CreateMatch(
        val ageGroupId: String? = null,
        val teamId: String? = null
    ) : Screen()

    @Serializable
    @SerialName("match")
    data class MatchDetails(val matchId: String) : Screen()

    // The live match console: pre-kickoff line-up, live squad/events, full-time summary.
    @Serializable
    @SerialName("live-match")
    data class LiveMatch(val matchId: String) : Screen()

    // The post-match report of a FINISHED match. `edit` opens the editor straight away (Fill in
    // report / Edit); otherwise it opens the read view.
    @Serializable
    @SerialName("match-report")
    data class MatchReport(val matchId: String, val edit: Boolean = false) : Screen()

    @Serializable
    @SerialName("player")
    data class PlayerDetails(
        val playerId: String,
        // Opened from Team Details' leaders (design 31f): the team's season and the stats filter
        // (a StatsFilter name) carry over. Null means the player's current season / All.
        val seasonId: String? = null,
        val statsFilter: String? = null
    ) : Screen()

    @Serializable
    @SerialName("edit-player")
    data class EditPlayer(val playerId: String) : Screen()
}
