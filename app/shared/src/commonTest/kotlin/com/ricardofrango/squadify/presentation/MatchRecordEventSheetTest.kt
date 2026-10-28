package com.ricardofrango.squadify.presentation

import com.ricardofrango.squadify.model.EventType
import com.ricardofrango.squadify.presentation.ui.matchrecord.DEFAULT_HALF_LENGTH
import com.ricardofrango.squadify.presentation.ui.matchrecord.DraftEvent
import com.ricardofrango.squadify.presentation.ui.matchrecord.needsPlayer
import com.ricardofrango.squadify.presentation.ui.matchrecord.periodForMinute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// The match record's event sheet (the developer, 2026-10-28).
class MatchRecordEventSheetTest {
    private fun draft(type: EventType, opponent: Boolean = false) =
        DraftEvent(key = "", id = null, type = type, minute = 10, period = 1, playerId = null, isOpponent = opponent)

    @Test
    fun aGameEnteredAfterwardsDefaultsTo30MinuteHalves() = assertEquals(30, DEFAULT_HALF_LENGTH)

    @Test
    fun theHalfFollowsTheMinuteCountedFromKickoff() {
        assertEquals(1, periodForMinute(0, 30, halves = 2))
        assertEquals(1, periodForMinute(30, 30, halves = 2))
        assertEquals(2, periodForMinute(31, 30, halves = 2))
        assertEquals(2, periodForMinute(58, 30, halves = 2))
    }

    @Test
    fun aSinglePeriodMatchIsAlwaysTheFirst() = assertEquals(1, periodForMinute(50, 30, halves = 1))

    @Test
    fun ourGoalCanHaveNoScorerButCardsAndOwnGoalsNeedThePlayer() {
        assertFalse(draft(EventType.GOAL).needsPlayer())
        assertTrue(draft(EventType.OWN_GOAL).needsPlayer())
        assertTrue(draft(EventType.YELLOW_CARD).needsPlayer())
        assertFalse(draft(EventType.OWN_GOAL, opponent = true).needsPlayer())
    }
}
