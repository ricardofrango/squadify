package com.ricardofrango.squadify.presentation

import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.presentation.ui.matchdetails.CallUpSection
import com.ricardofrango.squadify.presentation.ui.matchdetails.CallUpView
import com.ricardofrango.squadify.presentation.ui.matchdetails.MatchDetailsButton
import com.ricardofrango.squadify.presentation.ui.matchdetails.callUpView
import kotlin.test.Test
import kotlin.test.assertEquals

// Match details' call-up (the developer's rules, 2026-10-23): a draft saved with a button, and what
// each kind of user sees. A 7-a-side team: 7 players minimum.
class CallUpRulesTest {
    private val seven = (1..7).map { "p$it" }.toSet()
    private val eight = seven + "p8"

    private fun view(
        canManageCallUps: Boolean = true,
        canManageLiveMatch: Boolean = true,
        saved: Set<String> = emptySet(),
        selected: Set<String> = saved,
        status: MatchStatus = MatchStatus.SCHEDULED
    ): CallUpView = callUpView(status, canManageCallUps, canManageLiveMatch, saved, selected, minimum = 7)

    @Test
    fun belowTheMinimumSaveIsDisabled() =
        assertEquals(CallUpView(CallUpSection.EDITOR, MatchDetailsButton.SAVE_CALL_UP_DISABLED), view(selected = seven - "p1"))

    @Test
    fun reachingTheMinimumLetsTheCallUpBeSaved() =
        assertEquals(MatchDetailsButton.SAVE_CALL_UP, view(selected = seven).button)

    @Test
    fun onceSavedTheButtonStartsTheMatch() =
        assertEquals(MatchDetailsButton.START_MATCH, view(saved = seven, selected = seven).button)

    @Test
    fun changingASavedCallUpAsksToSaveAgain() {
        assertEquals(MatchDetailsButton.SAVE_CALL_UP, view(saved = seven, selected = eight).button)
        assertEquals(MatchDetailsButton.SAVE_CALL_UP, view(saved = eight, selected = eight - "p8" + "p9").button)
    }

    @Test
    fun goingBackToTheSavedCallUpStartsTheMatchAgain() =
        assertEquals(MatchDetailsButton.START_MATCH, view(saved = eight, selected = (eight - "p8") + "p8").button)

    @Test
    fun aManagerWhoCantStartTheGameSeesNoStartButton() =
        assertEquals(CallUpView(CallUpSection.EDITOR, MatchDetailsButton.NONE), view(canManageLiveMatch = false, saved = seven))

    @Test
    fun someoneWhoOnlyStartsTheGameSeesTheReadyCallUp() =
        assertEquals(CallUpView(CallUpSection.READ_ONLY, MatchDetailsButton.START_MATCH), view(canManageCallUps = false, saved = seven))

    @Test
    fun someoneWhoOnlyStartsTheGameIsToldItsNotReady() =
        assertEquals(CallUpView(CallUpSection.NOT_READY, MatchDetailsButton.NONE), view(canManageCallUps = false, saved = seven - "p7"))

    @Test
    fun someoneWithNeitherRightSeesOnlyTheDetails() =
        assertEquals(CallUpView(CallUpSection.HIDDEN, MatchDetailsButton.NONE), view(canManageCallUps = false, canManageLiveMatch = false, saved = seven))

    @Test
    fun aFinishedMatchIsReadOnlyWithNoButton() {
        assertEquals(CallUpView(CallUpSection.READ_ONLY, MatchDetailsButton.NONE), view(saved = seven, status = MatchStatus.FINISHED))
        assertEquals(CallUpView(CallUpSection.HIDDEN, MatchDetailsButton.NONE), view(canManageCallUps = false, canManageLiveMatch = false, saved = seven, status = MatchStatus.FINISHED))
    }
}
