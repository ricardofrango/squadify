package com.ricardofrango.footballmanager.presentation.ui.addplayerstoagegroup

import com.ricardofrango.footballmanager.model.AgeGroup
import com.ricardofrango.footballmanager.model.Player
import com.ricardofrango.footballmanager.model.Season

sealed class AddPlayersToAgeGroupUiState {
    data object Loading : AddPlayersToAgeGroupUiState()

    data class Idle(
        val ageGroups: List<AgeGroup> = emptyList(),
        val seasons: List<Season> = emptyList(),
        val selectedAgeGroupId: String? = null,
        // Club players eligible for the selected age group (old enough, not already in it). Refetched
        // whenever the age-group picker changes.
        val eligiblePlayers: List<Player> = emptyList(),
        val loadingEligible: Boolean = false,
        val selectedPlayerIds: Set<String> = emptySet(),
        val isSubmitting: Boolean = false,
        // Generic failure -- localized copy only, never a raw exception message. See AI_GUIDELINES.md
        // "No Raw Exception Messages in UI".
        val error: String? = null
    ) : AddPlayersToAgeGroupUiState() {

        val selectedAgeGroup: AgeGroup? get() = ageGroups.find { it.id == selectedAgeGroupId }

        fun seasonNameFor(ageGroup: AgeGroup): String? =
            seasons.find { it.id == ageGroup.seasonId }?.name
    }
}

sealed class AddPlayersToAgeGroupNavigationEvent {
    data object NavigateBackWithPlayersAdded : AddPlayersToAgeGroupNavigationEvent()
}
