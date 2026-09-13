package com.ricardofrango.squadify.presentation.ui.createagegroups

import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.Season

sealed class CreateAgeGroupsUiState {
    data object Loading : CreateAgeGroupsUiState()

    data class Idle(
        // The club's seasons, for the picker -- age groups belong to exactly one season and the user
        // needs to see (and be able to change) which one they're adding to, not have it guessed
        // silently.
        val seasons: List<Season> = emptyList(),
        val selectedSeasonId: String? = null,
        // Every age group in the club, kept unfiltered so switching the season picker only re-derives
        // usedBirthYears below instead of triggering another fetch.
        val ageGroups: List<AgeGroup> = emptyList(),
        val isSubmitting: Boolean = false,
        // Generic failure (network, unexpected server error, a 409 the client couldn't preempt, etc.)
        // -- localized copy only, never a raw exception message. See AI_GUIDELINES.md "No Raw
        // Exception Messages in UI".
        val error: String? = null
    ) : CreateAgeGroupsUiState() {

        val selectedSeason: Season? get() = seasons.find { it.id == selectedSeasonId }

        // Birth years already covered by an existing age group in the selected season (its full
        // endYear..startYear span, inclusive) -- excluded from every single-year row's picker so a
        // duplicate/overlap can't be selected in the first place (same idea as DatePickerSheet's
        // minDate/maxDate on the season screen). In-batch duplicates across this screen's own rows are
        // prevented the same way, by excluding sibling rows' picks too.
        val usedBirthYears: Set<Int>
            get() = ageGroups.filter { it.seasonId == selectedSeasonId }
                .flatMap { it.endYear..it.startYear }
                .toSet()
    }
}

sealed class CreateAgeGroupsNavigationEvent {
    data object NavigateBackWithAgeGroupsCreated : CreateAgeGroupsNavigationEvent()
}
