package com.ricardofrango.squadify.coach.presentation.ui.home

import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.model.SpaceAgeGroups
import com.ricardofrango.squadify.model.SpacePlan

/**
 * One row of the home's list: an age group and the space it's in, shown with the space's logo. [locked]:
 * over the space's [plan] for this coach (REQUIREMENTS §5c), listed but not opened.
 */
data class HomeAgeGroup(
    val ageGroup: AgeGroup,
    val space: Club,
    val isOwner: Boolean,
    val plan: SpacePlan,
    val locked: Boolean = false
) {
    val spaceName: String get() = space.name
}

/**
 * The coach's age groups as one list (REQUIREMENTS §5c): their own space's first, then the ones they're
 * staff on in other spaces, in the server's order of spaces, each space's youngest first. Home shows the
 * two under "My age groups" and "As staff" once there are both.
 */
fun homeAgeGroups(spaces: List<SpaceAgeGroups>): List<HomeAgeGroup> =
    spaces.sortedByDescending { it.isOwner }.flatMap { space ->
        space.ageGroups.sortedByDescending { it.endYear }.map {
            HomeAgeGroup(it, space.space, space.isOwner, space.plan, locked = it.id in space.lockedAgeGroupIds)
        }
    }

sealed class CoachHomeUiState {
    data object Loading : CoachHomeUiState()
    data object Failed : CoachHomeUiState()

    /** [ownSpace]: the space they own, where Create age group adds to; null until they set one up. */
    data class Loaded(val ageGroups: List<HomeAgeGroup>, val ownSpace: Club?) : CoachHomeUiState() {
        companion object {
            fun of(spaces: List<SpaceAgeGroups>) = Loaded(homeAgeGroups(spaces), spaces.firstOrNull { it.isOwner }?.space)
        }
    }
}

sealed class CoachHomeNavigationEvent {
    data object NavigateToLogin : CoachHomeNavigationEvent()
}
