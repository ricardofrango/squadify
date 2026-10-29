package com.ricardofrango.squadify.presentation

import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.presentation.ui.matchdetails.MatchDetailsUiState
import com.ricardofrango.squadify.presentation.ui.matchdetails.previewFinishedDetails
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

// Editing the match's details reloads the screen; it shouldn't throw away the call-up being edited,
// nor show "Save call-up" as saving (the developer, 2026-10-29).
class CallUpDraftReloadTest {
    private val base = previewFinishedDetails(canSeeRecord = true)
    private val calledUp = base.callUps.map { it.player }
    private val extra = Player("pool-1", "c1", "Zé", "Pool", LocalDate(2012, 1, 1), active = true)
    private val details = base.copy(poolPlayers = listOf(extra))

    @Test
    fun aReloadKeepsTheCallUpBeingEdited() {
        val draft = MatchDetailsUiState.Idle(details).let { it.copy(selectedIds = it.selectedIds - calledUp.first().id + extra.id) }
        val reloaded = MatchDetailsUiState.Idle(details).keepingDraftOf(draft)
        assertEquals(draft.selectedIds, reloaded.selectedIds)
    }

    @Test
    fun aPlayerNoLongerInThePoolDropsOutOfTheDraft() {
        val draft = MatchDetailsUiState.Idle(details).let { it.copy(selectedIds = it.selectedIds + extra.id) }
        val reloaded = MatchDetailsUiState.Idle(details.copy(poolPlayers = emptyList())).keepingDraftOf(draft)
        assertEquals(calledUp.map { it.id }.toSet(), reloaded.selectedIds)
    }

    @Test
    fun theFirstLoadStartsFromTheSavedCallUp() {
        val loaded = MatchDetailsUiState.Idle(details).keepingDraftOf(null)
        assertEquals(calledUp.map { it.id }.toSet(), loaded.selectedIds)
    }

    @Test
    fun editingTheMatchDoesNotMarkTheCallUpAsSaving() {
        val updating = MatchDetailsUiState.Idle(details).copy(isUpdatingMatch = true)
        assertFalse(updating.isSavingCallUp)
    }
}
