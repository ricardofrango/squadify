package com.ricardofrango.footballmanager.presentation.ui.creatematch

import com.ricardofrango.footballmanager.model.Opponent
import com.ricardofrango.footballmanager.model.Team
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
        val ageGroupName: String,
        val seasonName: String,
        val teamFixed: Boolean,
        val teams: List<Team> = emptyList(),
        val selectedTeamId: String? = null,
        val opponents: List<Opponent> = emptyList(),
        val selectedOpponentId: String? = null,
        val addingOpponent: Boolean = false,
        val newOpponentName: String = "",
        val date: LocalDate,
        val time: LocalTime,
        val location: String = "",
        val notes: String = "",
        val isSubmitting: Boolean = false,
        // Localized copy only, never a raw exception message (AI_GUIDELINES.md).
        val error: String? = null
    ) : CreateMatchUiState() {

        val selectedTeam: Team? get() = teams.find { it.id == selectedTeamId }

        val canSubmit: Boolean
            get() = selectedTeamId != null &&
                (selectedOpponentId != null || (addingOpponent && newOpponentName.isNotBlank()))
    }
}

sealed class CreateMatchNavigationEvent {
    data object NavigateBackWithMatchCreated : CreateMatchNavigationEvent()
}
