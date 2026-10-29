package com.ricardofrango.squadify.model

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class MatchTeamSizeTest {

    private val scheduled = Match("m-1", "club-1", "t-1", "opp-1", dateTime = Instant.fromEpochMilliseconds(1))

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
