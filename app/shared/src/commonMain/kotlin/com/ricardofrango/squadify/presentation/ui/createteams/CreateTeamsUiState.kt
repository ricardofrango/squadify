package com.ricardofrango.squadify.presentation.ui.createteams

import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.Season

sealed class CreateTeamsUiState {
    data object Loading : CreateTeamsUiState()

    data class Idle(
        // False where the server picks the season (the coach app): no season shown, and the teams go
        // in the age group's own season.
        val pickSeason: Boolean = true,
        // The club's seasons, for the picker. Defaults to the most recent by end date (see
        // CreateTeamsViewModel) but stays changeable.
        val seasons: List<Season> = emptyList(),
        val selectedSeasonId: String? = null,
        // Every age group in the club, kept unfiltered; the screen shows only the ones under the
        // selected season. No default selection -- the user must pick one, and the screen shows an
        // error if they don't.
        val ageGroups: List<AgeGroup> = emptyList(),
        val selectedAgeGroupId: String? = null,
        val isSubmitting: Boolean = false,
        // Generic failure (network, unexpected server error, etc.) -- localized copy only, never a raw
        // exception message. See AI_GUIDELINES.md "No Raw Exception Messages in UI".
        val error: String? = null
    ) : CreateTeamsUiState() {

        val selectedSeason: Season? get() = seasons.find { it.id == selectedSeasonId }

        /** Age groups belonging to the currently selected season -- the picker's options. */
        val ageGroupsForSeason: List<AgeGroup>
            get() = ageGroups.filter { it.seasonId == selectedSeasonId }
    }
}

sealed class CreateTeamsNavigationEvent {
    data object NavigateBackWithTeamsCreated : CreateTeamsNavigationEvent()
}
