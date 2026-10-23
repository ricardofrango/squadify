package com.ricardofrango.squadify.presentation.ui.addstafftoagegroup

import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.ClubMemberSummary
import com.ricardofrango.squadify.model.Season
import com.ricardofrango.squadify.model.AgeGroupRole

sealed class AddStaffToAgeGroupUiState {
    data object Loading : AddStaffToAgeGroupUiState()

    data class Idle(
        val ageGroups: List<AgeGroup> = emptyList(),
        val seasons: List<Season> = emptyList(),
        val selectedAgeGroupId: String? = null,
        // Club members eligible for the selected age group (already a club member, not already staff
        // of it). Refetched whenever the age-group picker changes.
        val eligibleMembers: List<ClubMemberSummary> = emptyList(),
        val loadingEligible: Boolean = false,
        // Selected member userIds mapped to the role they'll be added with -- presence in the map is
        // the selection itself, so picking a role also picks the member.
        val selectedRoles: Map<String, AgeGroupRole> = emptyMap(),
        val isSubmitting: Boolean = false,
        // Generic failure -- localized copy only, never a raw exception message. See AI_GUIDELINES.md
        // "No Raw Exception Messages in UI".
        val error: String? = null
    ) : AddStaffToAgeGroupUiState() {

        val selectedAgeGroup: AgeGroup? get() = ageGroups.find { it.id == selectedAgeGroupId }

        fun seasonNameFor(ageGroup: AgeGroup): String? =
            seasons.find { it.id == ageGroup.seasonId }?.name
    }
}

sealed class AddStaffToAgeGroupNavigationEvent {
    data object NavigateBackWithStaffAdded : AddStaffToAgeGroupNavigationEvent()
}
