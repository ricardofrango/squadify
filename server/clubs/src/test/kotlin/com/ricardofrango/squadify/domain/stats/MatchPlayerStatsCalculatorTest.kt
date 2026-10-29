package com.ricardofrango.squadify.domain.stats

import com.ricardofrango.squadify.model.CallUpStatus
import com.ricardofrango.squadify.model.EventType
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchCallUp
import com.ricardofrango.squadify.model.MatchEvent
import com.ricardofrango.squadify.model.MatchStatus
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Minutes played and per-player counts for a finished match. The standard fixture is two 45-minute
 * halves; the first half ended at 47' (2 minutes of stoppage time), the second at 93'.
 */
class MatchPlayerStatsCalculatorTest {

    private val kickoff = Instant.fromEpochMilliseconds(0)
    private val secondHalfStart = kickoff + 60.minutes

    private val match = Match(
        id = "m", clubId = "c", teamId = "t", opponentId = "o", dateTime = kickoff,
        status = MatchStatus.FINISHED, halfLengthMinutes = 45, kickoffAt = kickoff,
        secondHalfStartedAt = secondHalfStart, firstHalfEndMinute = 47, fullTimeMinute = 93
    )

    private fun callUp(playerId: String, starting: Boolean) =
        MatchCallUp("cu-$playerId", "m", playerId, CallUpStatus.CALLED_UP, isStarting = starting)

    // `half` is the recorded period; createdAt matches it too, so legacy fallback tests can drop period.
    private var seq = 0
    private fun event(type: EventType, playerId: String?, minute: Int, half: Int, isOpponent: Boolean = false): MatchEvent {
        val base = if (half == 1) kickoff else secondHalfStart
        return MatchEvent("e${seq++}", "m", type, minute, playerId, isOpponent = isOpponent, createdAt = base + (seq).minutes / 100, period = half)
    }

    private fun statsFor(playerId: String, callUps: List<MatchCallUp>, events: List<MatchEvent>, m: Match = match) =
        MatchPlayerStatsCalculator.calculate(m, callUps, events).single { it.playerId == playerId }

    @Test
    fun aStarterWhoPlaysTheWholeMatchGetsBothHalvesIncludingStoppageTime() {
        val stats = statsFor("a", listOf(callUp("a", true)), emptyList())
        assertEquals(47 + (93 - 45), stats.minutesPlayed)
        assertEquals(true, stats.started)
    }

    @Test
    fun anUnusedSubstitutePlaysZeroMinutes() {
        val stats = statsFor("b", listOf(callUp("b", false)), emptyList())
        assertEquals(0, stats.minutesPlayed)
        assertEquals(false, stats.started)
    }

    @Test
    fun aSubstitutionInTheSecondHalfSplitsTheMinutes() {
        val callUps = listOf(callUp("a", true), callUp("b", false))
        val events = listOf(
            event(EventType.SUBSTITUTION_OUT, "a", 70, half = 2),
            event(EventType.SUBSTITUTION_IN, "b", 70, half = 2)
        )
        assertEquals(47 + (70 - 45), statsFor("a", callUps, events).minutesPlayed)
        assertEquals(93 - 70, statsFor("b", callUps, events).minutesPlayed)
    }

    @Test
    fun firstHalfStoppageTimeIsNotMistakenForTheSecondHalf() {
        // Both events are at 46', but one was logged in first-half stoppage time and the other after
        // the restart. The half is decided by when it was logged, not by the minute.
        val callUps = listOf(callUp("a", true), callUp("b", true))
        val events = listOf(
            event(EventType.SUBSTITUTION_OUT, "a", 46, half = 1),
            event(EventType.SUBSTITUTION_OUT, "b", 46, half = 2)
        )
        assertEquals(46, statsFor("a", callUps, events).minutesPlayed)
        assertEquals(47 + (46 - 45), statsFor("b", callUps, events).minutesPlayed)
    }

    @Test
    fun aSubstitutionAtHalfTimeGivesTheIncomingPlayerTheWholeSecondHalf() {
        // Logged during the break (before secondHalfStartedAt), at 45'.
        val callUps = listOf(callUp("a", true), callUp("b", false))
        val events = listOf(
            event(EventType.SUBSTITUTION_OUT, "a", 45, half = 1),
            event(EventType.SUBSTITUTION_IN, "b", 45, half = 1)
        )
        assertEquals(45, statsFor("a", callUps, events).minutesPlayed)
        assertEquals((47 - 45) + (93 - 45), statsFor("b", callUps, events).minutesPlayed)
    }

    @Test
    fun aRedCardEndsThePlayersMatch() {
        val stats = statsFor("a", listOf(callUp("a", true)), listOf(event(EventType.RED_CARD, "a", 30, half = 1)))
        assertEquals(30, stats.minutesPlayed)
        assertEquals(1, stats.redCards)
    }

    @Test
    fun aPlayerSubbedOnAndLaterOffGetsOnlyTheirSpell() {
        val callUps = listOf(callUp("b", false))
        val events = listOf(
            event(EventType.SUBSTITUTION_IN, "b", 20, half = 1),
            event(EventType.SUBSTITUTION_OUT, "b", 60, half = 2)
        )
        assertEquals((47 - 20) + (60 - 45), statsFor("b", callUps, events).minutesPlayed)
    }

    @Test
    fun aMatchEndedWithoutASecondHalfCountsOnlyTheFirst() {
        // Finished straight from HALF_TIME / LIVE in the first half: no secondHalfStartedAt.
        val oneHalf = match.copy(secondHalfStartedAt = null, firstHalfEndMinute = 47, fullTimeMinute = 47)
        assertEquals(47, statsFor("a", listOf(callUp("a", true)), emptyList(), oneHalf).minutesPlayed)
    }

    @Test
    fun missingEndMinutesFallBackToTheNominalHalfLength() {
        val legacy = match.copy(firstHalfEndMinute = null, fullTimeMinute = null)
        assertEquals(90, statsFor("a", listOf(callUp("a", true)), emptyList(), legacy).minutesPlayed)
    }

    @Test
    fun countsOnlyTheirOwnNonOpponentEvents() {
        val callUps = listOf(callUp("a", true), callUp("b", true))
        val events = listOf(
            event(EventType.GOAL, "a", 10, half = 1),
            event(EventType.GOAL, "a", 80, half = 2),
            event(EventType.ASSIST, "b", 10, half = 1),
            event(EventType.OWN_GOAL, "b", 50, half = 2),
            event(EventType.YELLOW_CARD, "a", 55, half = 2),
            event(EventType.GOAL, null, 60, half = 2, isOpponent = true),
            event(EventType.GOAL, null, 65, half = 2) // unattributed goal of ours
        )
        val a = statsFor("a", callUps, events)
        val b = statsFor("b", callUps, events)
        assertEquals(2, a.goals)
        assertEquals(1, a.yellowCards)
        assertEquals(0, a.assists)
        assertEquals(1, b.assists)
        assertEquals(1, b.ownGoals)
        assertEquals(0, b.goals)
    }

    @Test
    fun everyCalledUpPlayerGetsARowAndNoOneElseDoes() {
        val callUps = listOf(callUp("a", true), callUp("b", false))
        val events = listOf(event(EventType.GOAL, "not-called-up", 10, half = 1))
        val rows = MatchPlayerStatsCalculator.calculate(match, callUps, events)
        assertEquals(setOf("a", "b"), rows.map { it.playerId }.toSet())
    }

    @Test
    fun theRecordedPeriodWinsOverTimestamps() {
        // A half-time substitution (period 1), but a "Set minute" correction after the restart moved
        // secondHalfStartedAt to before it was logged. Timestamps would wrongly put it in the second half.
        val corrected = match.copy(secondHalfStartedAt = kickoff + 30.minutes)
        val callUps = listOf(callUp("a", true), callUp("b", false))
        val events = listOf(
            event(EventType.SUBSTITUTION_OUT, "a", 45, half = 1).copy(createdAt = kickoff + 50.minutes),
            event(EventType.SUBSTITUTION_IN, "b", 45, half = 1).copy(createdAt = kickoff + 50.minutes)
        )
        assertEquals(45, statsFor("a", callUps, events, corrected).minutesPlayed)
        assertEquals((47 - 45) + (93 - 45), statsFor("b", callUps, events, corrected).minutesPlayed)
    }

    @Test
    fun eventsWithoutAPeriodFallBackToTimestamps() {
        val callUps = listOf(callUp("a", true))
        val events = listOf(event(EventType.SUBSTITUTION_OUT, "a", 60, half = 2).copy(period = null))
        assertEquals(47 + (60 - 45), statsFor("a", callUps, events).minutesPlayed)
    }
}
