package com.ricardofrango.footballmanager.presentation.ui.matchdetails

import com.ricardofrango.footballmanager.model.MatchDetails

sealed class MatchDetailsUiState {
    data object Loading : MatchDetailsUiState()

    data class Error(val message: String) : MatchDetailsUiState()

    data class Idle(
        val details: MatchDetails,
        val sort: CallUpSort = CallUpSort.STATUS,
        // A call-up / status / manage write is in flight (edits are applied optimistically first).
        val isSubmitting: Boolean = false,
        // An optimistic write was rolled back -- shown as a transient message, not a full error state.
        val transientError: String? = null
    ) : MatchDetailsUiState()
}

/** Ordering of the called-up list. Local screen state, not persisted. */
enum class CallUpSort { STATUS, NAME }

sealed class MatchDetailsNavEvent {
    /** The match has been started (status is LIVE) -- open the live match screen. */
    data class EnterLiveMatch(val matchId: String) : MatchDetailsNavEvent()
}
