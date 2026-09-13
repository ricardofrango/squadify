package com.ricardofrango.squadify.presentation.ui.livematch

import com.ricardofrango.squadify.model.MatchDetails
import com.ricardofrango.squadify.model.MatchEvent
import com.ricardofrango.squadify.model.MatchScore

enum class LiveMatchTab { SQUAD, EVENTS }

/**
 * One [Idle] state covers every phase of the match (pre-kickoff, live, half-time, full-time) --
 * the screen branches on `details.match.status` to decide which layout to render, the same way
 * `ClubDetailsUiState.Idle` covers every tab. [events] / [score] are empty/zero before kickoff
 * (nothing to show yet) and kept live afterwards, both by the REST responses of the actions below and
 * by the WebSocket subscription (see LiveMatchViewModel).
 */
sealed class LiveMatchUiState {
    data object Loading : LiveMatchUiState()
    data class Error(val message: String) : LiveMatchUiState()

    data class Idle(
        val details: MatchDetails,
        val events: List<MatchEvent> = emptyList(),
        val score: MatchScore,
        val selectedTab: LiveMatchTab = LiveMatchTab.SQUAD,
        // In-flight kickoff / half-time / full-time / substitution calls -- disables the triggering
        // control so a slow connection can't be double-tapped.
        val isSubmitting: Boolean = false,
        val error: String? = null
    ) : LiveMatchUiState()
}
