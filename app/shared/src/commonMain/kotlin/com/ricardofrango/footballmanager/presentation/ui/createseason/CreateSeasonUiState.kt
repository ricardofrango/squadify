package com.ricardofrango.footballmanager.presentation.ui.createseason

import com.ricardofrango.footballmanager.model.Season

sealed class CreateSeasonUiState {
    data object Loading : CreateSeasonUiState()

    data class Idle(
        // The club's existing seasons, fetched once on load -- used to name the specific season a
        // duplicate-dates submission collided with, without needing a dedicated server response for it
        // (the server's uniqueness check is exactly club+type+startDate+endDate, so a local match on
        // those same fields identifies the same row the server would have rejected against).
        val existingSeasons: List<Season> = emptyList(),
        val isSubmitting: Boolean = false,
        // Generic failure (network, unexpected server error, etc.) -- localized copy only, never a raw
        // exception message. See AI_GUIDELINES.md "No Raw Exception Messages in UI".
        val error: String? = null,
        // True instead of setting `error`, specifically for the 409 duplicate-season case, so the
        // screen can highlight both date fields. `conflictingSeasonName` is set alongside it when the
        // colliding season could be identified locally (see CreateSeasonViewModel); when it's null the
        // screen falls back to a generic "a season with these dates already exists" message.
        val isConflict: Boolean = false,
        val conflictingSeasonName: String? = null
    ) : CreateSeasonUiState()
}

sealed class CreateSeasonNavigationEvent {
    data object NavigateBackWithSeasonCreated : CreateSeasonNavigationEvent()
}
