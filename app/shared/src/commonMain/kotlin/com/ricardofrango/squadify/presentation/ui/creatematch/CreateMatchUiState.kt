package com.ricardofrango.squadify.presentation.ui.creatematch

import com.ricardofrango.squadify.model.MatchType
import com.ricardofrango.squadify.model.MatchVenue
import com.ricardofrango.squadify.model.Opponent
import com.ricardofrango.squadify.model.Team
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

sealed class CreateMatchUiState {
    data object Loading : CreateMatchUiState()

    data class Error(val message: String) : CreateMatchUiState()

    /**
     * @param teamFixed true when the screen was opened from a team (Team Details) -- [teams] then
     *   holds only that team and the picker is replaced by a locked row. From an age group it holds
     *   all the age group's teams and the user must pick one.
     */
    data class Idle(
        val clubId: String,
        val clubName: String,
        // The concrete age group this screen is scoped to -- resolved whether the screen was opened
        // from an age group directly or from one of its teams (see CreateMatchViewModel.load). Passed
        // to the Opponents screen so a coach of specifically this age group can view/add opponents,
        // not just a club admin/coordinator.
        val ageGroupId: String,
        val ageGroupName: String,
        val seasonName: String,
        val teamFixed: Boolean,
        val teams: List<Team> = emptyList(),
        val selectedTeamId: String? = null,
        val opponents: List<Opponent> = emptyList(),
        val selectedOpponentId: String? = null,
        // No default: every new match has to be typified before it can be scheduled.
        val matchType: MatchType? = null,
        // Set when Schedule is tapped with no type picked -- paints the type rows red until one is.
        val matchTypeError: Boolean = false,
        // 2 halves unless switched to a single period (no half time).
        val halves: Int = 2,
        // Home, away or neutral -- no default, it has to be picked; it prefills [location].
        val venue: MatchVenue? = null,
        // Set when Schedule is tapped with no venue picked, like matchTypeError.
        val venueError: Boolean = false,
        // The club's ground (stadium location, else its address), for a home match's location.
        val clubLocation: String? = null,
        val date: LocalDate,
        val time: LocalTime,
        val location: String = "",
        val notes: String = "",
        val isSubmitting: Boolean = false,
        // Localized copy only, never a raw exception message (AI_GUIDELINES.md).
        val error: String? = null
    ) : CreateMatchUiState() {

        val selectedTeam: Team? get() = teams.find { it.id == selectedTeamId }

        val selectedOpponent: Opponent? get() = opponents.find { it.id == selectedOpponentId }

        val canSubmit: Boolean
            get() = selectedTeamId != null && selectedOpponentId != null && matchType != null && venue != null
    }
}

sealed class CreateMatchNavigationEvent {
    data object NavigateBackWithMatchCreated : CreateMatchNavigationEvent()
}
