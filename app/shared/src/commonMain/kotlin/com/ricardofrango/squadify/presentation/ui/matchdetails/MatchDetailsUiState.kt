package com.ricardofrango.squadify.presentation.ui.matchdetails

import com.ricardofrango.squadify.model.MatchDetails

sealed class MatchDetailsUiState {
    data object Loading : MatchDetailsUiState()

    data class Error(val message: String) : MatchDetailsUiState()

    data class Idle(
        val details: MatchDetails,
        val sort: CallUpSort = CallUpSort.POSITION,
        // A call-up / status / manage write is in flight (edits are applied optimistically first).
        val isSubmitting: Boolean = false,
        // An optimistic write was rolled back -- shown as a transient message, not a full error state.
        val transientError: String? = null,
        // The call-up on screen: a draft until "Save call-up" sends it. Starts as the saved one.
        val selectedIds: Set<String> = details.callUps.map { it.player.id }.toSet()
    ) : MatchDetailsUiState() {
        /** The call-up as the server has it. */
        val savedIds: Set<String> get() = details.callUps.map { it.player.id }.toSet()

        /** Every player who can be called up: the saved call-up and the rest of the pool. */
        private val allPlayers get() = (details.callUps.map { it.player } + details.poolPlayers).distinctBy { it.id }

        /** The draft call-up's players, and the pool players not in it. */
        val selectedPlayers get() = allPlayers.filter { it.id in selectedIds }
        val availablePlayers get() = allPlayers.filterNot { it.id in selectedIds }

        /** The call-up on screen differs from the saved one: leaving asks before throwing it away. */
        val hasUnsavedCallUp: Boolean get() = details.canManageCallUps && selectedIds != savedIds
    }
}

/** Ordering of the called-up list. Local screen state, not persisted. */
enum class CallUpSort { NAME, POSITION }

sealed class MatchDetailsNavEvent {
    /** The user tapped the bottom bar's start/enter button -- open the live match screen, keeping
     * this screen on the back stack so returning from it still shows the match details. */
    data class EnterLiveMatch(val matchId: String) : MatchDetailsNavEvent()

    /** Arrived here (or reloaded) to find the match is already LIVE/HALF_TIME -- this screen isn't
     * where a live match should be viewed, so hand off to the live match screen immediately,
     * replacing this entry on the back stack rather than stacking under it (see AppNavigation.kt). */
    data class RedirectToLiveMatch(val matchId: String) : MatchDetailsNavEvent()

    /** The match was just deleted -- nothing left to show here, so the screen navigates back. */
    data object MatchDeleted : MatchDetailsNavEvent()
}
