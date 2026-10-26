package com.ricardofrango.squadify.presentation

import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.isNotPlayed
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// A game never started well past its kickoff is "Not played": its own section, and a Home alert.
class NotPlayedTest {

    private val kickoff = Instant.fromEpochMilliseconds(1_800_000_000_000)
    private fun match(status: MatchStatus) = Match("m", "c", "t", "o", dateTime = kickoff, status = status)

    @Test
    fun aScheduledGameTwoHoursPastKickoffIsNotPlayed() {
        assertTrue(match(MatchStatus.SCHEDULED).isNotPlayed(kickoff + 2.hours))
        assertTrue(match(MatchStatus.SCHEDULED).isNotPlayed(kickoff + 3.hours))
    }

    @Test
    fun aLateStartIsStillAStart() {
        assertFalse(match(MatchStatus.SCHEDULED).isNotPlayed(kickoff))
        assertFalse(match(MatchStatus.SCHEDULED).isNotPlayed(kickoff + 119.minutes))
    }

    @Test
    fun onlyAScheduledGameCanBeNotPlayed() {
        val later = kickoff + 5.hours
        listOf(MatchStatus.LIVE, MatchStatus.HALF_TIME, MatchStatus.FINISHED, MatchStatus.CANCELLED).forEach {
            assertFalse(match(it).isNotPlayed(later))
        }
    }
}
