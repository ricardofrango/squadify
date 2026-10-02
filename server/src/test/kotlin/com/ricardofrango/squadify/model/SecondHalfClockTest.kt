package com.ricardofrango.squadify.model

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The second half always restarts at the half length (30:00 for 30-minute halves), however long the
 * first half ran, and it reads 30:00 even on a device whose own clock is off from the server's.
 */
class SecondHalfClockTest {

    private val t0 = Instant.fromEpochMilliseconds(1_000_000)

    private fun secondHalfStarted(firstHalfEndMinute: Int, at: Instant) = Match(
        id = "m", clubId = "c", teamId = "t", opponentId = "o", dateTime = t0, status = MatchStatus.LIVE,
        halfLengthMinutes = 30, kickoffAt = t0, secondHalfStartedAt = at, firstHalfEndMinute = firstHalfEndMinute
    )

    @Test
    fun theSecondHalfStartsAt30WhetherTheFirstEndedAt29Or31() {
        val start = t0 + 45.minutes
        for (firstHalfEnd in listOf(29, 30, 31)) {
            assertEquals(30 * 60, elapsedMatchSeconds(secondHalfStarted(firstHalfEnd, start), start))
            assertEquals(30 * 60 + 65, elapsedMatchSeconds(secondHalfStarted(firstHalfEnd, start), start + 65.seconds))
        }
    }

    @Test
    fun theOffsetIsMeasuredAgainstTheMidpointOfTheRequest() {
        // Sent at t0, answered 400ms later; the server's clock is 20s ahead of this device's.
        val serverNow = t0 + 200.milliseconds + 20.seconds
        assertEquals(20.seconds, estimateServerClockOffset(serverNow, t0, t0 + 400.milliseconds))
    }

    @Test
    fun aDeviceBehindTheServerStillStartsTheSecondHalfAt30() {
        // The server started the second half at its time S; this device's clock is 20s behind.
        val serverStart = t0 + 45.minutes
        val match = secondHalfStarted(31, serverStart)
        val deviceNow = serverStart - 20.seconds

        // Counting on the device's own clock read 29:40 (the bug); counting on the server's reads 30:00.
        assertEquals(30 * 60 - 20, elapsedMatchSeconds(match, deviceNow))
        assertEquals(30 * 60, elapsedMatchSeconds(match, deviceNow + 20.seconds))
    }
}
