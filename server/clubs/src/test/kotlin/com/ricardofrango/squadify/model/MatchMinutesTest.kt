package com.ricardofrango.squadify.model

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The live side of the shared minutes rules (design 36): minutes while a half is running, the current
 * spell ("on for"), "off since", rolling substitutions, and the fair share. The full-time totals are
 * covered through MatchPlayerStatsCalculatorTest, which uses the same function.
 */
class MatchMinutesTest {

    private val kickoff = Instant.fromEpochMilliseconds(0)
    private val secondHalfStart = kickoff + 40.minutes

    // 7-a-side, 25-minute halves.
    private val firstHalf = Match(
        id = "m", clubId = "c", teamId = "t", opponentId = "o", dateTime = kickoff,
        status = MatchStatus.LIVE, halfLengthMinutes = 25, kickoffAt = kickoff
    )
    private val secondHalf = firstHalf.copy(secondHalfStartedAt = secondHalfStart, firstHalfEndMinute = 27)

    private fun callUp(id: String, starting: Boolean) = MatchCallUp("cu-$id", "m", id, CallUpStatus.CALLED_UP, isStarting = starting)

    private var seq = 0
    private fun event(type: EventType, id: String, minute: Int, half: Int = 1): MatchEvent {
        val base = if (half == 1) kickoff else secondHalfStart
        return MatchEvent("e${seq++}", "m", type, minute, id, createdAt = base + seq.minutes / 100, period = half)
    }

    @Test
    fun startersCountUpToTheLiveMinuteAndTheBenchStaysAtZero() {
        val m = playerMinutes(firstHalf, listOf(callUp("a", true), callUp("b", false)), emptyList(), liveMinute = 18)
        assertEquals(18, m.getValue("a").minutes)
        assertEquals(18, m.getValue("a").stintMinutes)
        assertTrue(m.getValue("a").onPitch)
        assertEquals(0, m.getValue("b").minutes)
        assertFalse(m.getValue("b").hasPlayed)
        assertNull(m.getValue("b").lastOffMinute)
    }

    @Test
    fun rollingSubstitutionsAddUpEverySpellAndTheStintIsOnlyTheCurrentOne() {
        // On 0-9, off 9-12, back on from 12' -- now 18'.
        val events = listOf(
            event(EventType.SUBSTITUTION_OUT, "a", 9), event(EventType.SUBSTITUTION_IN, "b", 9),
            event(EventType.SUBSTITUTION_OUT, "b", 12), event(EventType.SUBSTITUTION_IN, "a", 12)
        )
        val m = playerMinutes(firstHalf, listOf(callUp("a", true), callUp("b", false)), events, liveMinute = 18)
        assertEquals(9 + 6, m.getValue("a").minutes)
        assertEquals(6, m.getValue("a").stintMinutes)
        assertEquals(3, m.getValue("b").minutes)
        assertFalse(m.getValue("b").onPitch)
        assertEquals(12, m.getValue("b").lastOffMinute)
        assertTrue(m.getValue("b").hasPlayed)
    }

    @Test
    fun aSpellAcrossHalfTimeKeepsCountingInTheSecondHalf() {
        // Came on at 20' of a first half that ended at 27'; the second half restarts at 25' and it's now 31'.
        val events = listOf(event(EventType.SUBSTITUTION_IN, "b", 20), event(EventType.SUBSTITUTION_OUT, "a", 20))
        val m = playerMinutes(secondHalf, listOf(callUp("a", true), callUp("b", false)), events, liveMinute = 31)
        assertEquals(7 + 6, m.getValue("b").minutes)
        assertEquals(13, m.getValue("b").stintMinutes)
        assertEquals(20, m.getValue("a").minutes)
    }

    @Test
    fun atHalfTimeTheFirstHalfIncludingStoppageIsTheTotal() {
        val halfTime = firstHalf.copy(status = MatchStatus.HALF_TIME, firstHalfEndMinute = 27)
        val m = playerMinutes(halfTime, listOf(callUp("a", true)), emptyList())
        assertEquals(27, m.getValue("a").minutes)
        assertEquals(27, elapsedMatchMinutes(matchHalves(halfTime)))
    }

    @Test
    fun aRedCardFreezesTheMinutesAndTheyCannotComeBack() {
        val events = listOf(event(EventType.RED_CARD, "a", 10), event(EventType.SUBSTITUTION_IN, "a", 15))
        val m = playerMinutes(firstHalf, listOf(callUp("a", true)), events, liveMinute = 20).getValue("a")
        assertEquals(10, m.minutes)
        assertTrue(m.sentOff)
        assertFalse(m.onPitch)
        assertEquals(10, m.lastOffMinute)
    }

    @Test
    fun theFairShareSplitsTheMatchMinutesAcrossTheSquad() {
        // 18' gone, 7 on the pitch, 12 called up: 10.5' each.
        assertEquals(10.5, fairShareMinutes(18, 7, 12))
        assertTrue(isBelowShare(5, 10.5, 18))
        assertFalse(isBelowShare(9, 10.5, 18))
        // Nobody is flagged in the first minutes.
        assertFalse(isBelowShare(0, 0.6, 1))
    }
}
