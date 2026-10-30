package com.ricardofrango.squadify.coach.presentation.ui.home

import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.CoachSpace

sealed class CoachHomeUiState {
    data object Loading : CoachHomeUiState()
    data object Failed : CoachHomeUiState()
    // The age groups the coach sees: all of the space's for the owner, their own for staff.
    data class Loaded(val space: CoachSpace, val ageGroups: List<AgeGroup>) : CoachHomeUiState()
}

sealed class CoachHomeNavigationEvent {
    // No space yet (e.g. a session restored before the first setup was finished).
    data object NavigateToSetup : CoachHomeNavigationEvent()
    data object NavigateToLogin : CoachHomeNavigationEvent()
}
