package com.ricardofrango.clubmanager.presentation.ui.createplayers

/**
 * No `Loading` variant -- this screen fetches nothing, so it's usable the moment it opens. Only the
 * submit lifecycle needs tracking. `error` is a static localized copy, never a raw exception message
 * (see AI_GUIDELINES.md "No Raw Exception Messages in UI").
 */
data class CreatePlayersUiState(
    val isSubmitting: Boolean = false,
    val error: String? = null
)

sealed class CreatePlayersNavigationEvent {
    data object NavigateBackWithPlayersCreated : CreatePlayersNavigationEvent()
}
