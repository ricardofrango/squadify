package com.ricardofrango.squadify.model

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class MatchTeamSizeTest {

    private val team = Team("t-1", "club-1", "ag-1", "s-1", "Sub-13 A", active = true, teamSize = 9)
    private val scheduled = Match("m-1", "club-1", "t-1", "opp-1", dateTime = Instant.fromEpochMilliseconds(1))

    @Test
    fun aMatchPlaysItsOwnCountElseTheTeamsElseEleven() {
        assertEquals(7, scheduled.copy(teamSize = 7).effectiveTeamSize(team))
        assertEquals(9, scheduled.effectiveTeamSize(team))
        assertEquals(11, scheduled.effectiveTeamSize(team.copy(teamSize = null)))
    }

    @Test
    fun theCountChangesBeforeKickoffOrForAGameEnteredAfterwardsNeverOnceFollowedLive() {
        assertTrue(scheduled.canChangeTeamSize)
        assertTrue(scheduled.copy(status = MatchStatus.FINISHED, enteredAfterGame = true).canChangeTeamSize)
        val kickedOff = scheduled.copy(kickoffAt = Instant.fromEpochMilliseconds(2))
        assertFalse(kickedOff.copy(status = MatchStatus.LIVE).canChangeTeamSize)
        assertFalse(kickedOff.copy(status = MatchStatus.HALF_TIME).canChangeTeamSize)
        assertFalse(kickedOff.copy(status = MatchStatus.FINISHED).canChangeTeamSize)
        assertFalse(scheduled.copy(status = MatchStatus.CANCELLED).canChangeTeamSize)
    }
}
