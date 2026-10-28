package com.ricardofrango.squadify.presentation

import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.presentation.ui.matchdetails.MatchDetailsUiState
import com.ricardofrango.squadify.presentation.ui.matchdetails.previewFinishedDetails
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

// The call-up editor is one list: every candidate, the selected ones marked. Selecting a player
// doesn't move them to another section (the developer, 2026-10-28).
class CallUpCandidatesTest {
    private val base = previewFinishedDetails(canSeeRecord = true)
    private val calledUp = base.callUps.map { it.player }
    private val extra = Player("pool-1", "c1", "Zé", "Pool", LocalDate(2012, 1, 1), active = true)
    // The pool can repeat a called-up player: they're listed once.
    private val details = base.copy(poolPlayers = listOf(calledUp.first(), extra))

    @Test
    fun theListHasTheCallUpAndThePoolOnce() {
        val state = MatchDetailsUiState.Idle(details)
        assertEquals((calledUp + extra).map { it.id }, state.candidates.map { it.id })
    }

    @Test
    fun selectingOrUnselectingKeepsEveryoneInTheList() {
        val state = MatchDetailsUiState.Idle(details)
        val toggled = state.copy(selectedIds = state.selectedIds - calledUp.first().id + extra.id)
        assertEquals(state.candidates, toggled.candidates)
        assertEquals(calledUp.drop(1).map { it.id } + extra.id, toggled.selectedPlayers.map { it.id })
    }
}
