package com.ricardofrango.squadify.domain.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

// A live socket that reopens itself (keepConnected): after each drop it waits 1s, 2s, 5s, then 15s,
// and starts again from 1s once a connection has held.
@OptIn(ExperimentalCoroutinesApi::class)
class LiveFeedTest {

    @Test
    fun eachDropIsRetriedWaitingLongerEachTime() = runTest {
        val opened = mutableListOf<Long>()
        val seen = mutableListOf<LiveFeedEvent<String>>()
        // Every connection fails straight away, as with no network.
        val job = launch {
            keepConnected<String>(timeSource = testTimeSource) {
                flow { opened += currentTime; throw IllegalStateException("no network") }
            }.collect { seen += it }
        }
        advanceTimeBy(40.seconds)
        job.cancel()

        // Tried at 0, then 1s, 2s, 5s and 15s apart.
        assertEquals(listOf(0L, 1_000L, 3_000L, 8_000L, 23_000L, 38_000L), opened)
        assertEquals(LiveFeedEvent.Disconnected, seen.first())
    }

    @Test
    fun aConnectionThatHeldStartsTheWaitsAgain() = runTest {
        val opened = mutableListOf<Long>()
        val seen = mutableListOf<LiveFeedEvent<String>>()
        var attempt = 0
        val job = launch {
            keepConnected<String>(timeSource = testTimeSource) {
                flow {
                    opened += currentTime
                    attempt++
                    // Two quick failures, then one that holds for a minute with a message, then quick failures.
                    if (attempt == 3) {
                        emit(SocketEvent.Open)
                        emit(SocketEvent.Message("goal"))
                        delay(60.seconds)
                    }
                    throw IllegalStateException("dropped")
                }
            }.collect { seen += it }
        }
        advanceTimeBy(65.seconds)
        runCurrent()
        job.cancel()

        // 0, +1s, +2s (holds 60s, drops at 63s), then +1s again: the waits started over.
        assertEquals(listOf(0L, 1_000L, 3_000L, 64_000L), opened)
        assertEquals(
            listOf<LiveFeedEvent<String>>(
                LiveFeedEvent.Disconnected, LiveFeedEvent.Disconnected,
                LiveFeedEvent.Connected, LiveFeedEvent.Update("goal"), LiveFeedEvent.Disconnected,
                LiveFeedEvent.Disconnected
            ),
            seen
        )
    }

    @Test
    fun anOpenSocketIsKeptWhileItsCollected() = runTest {
        var opened = 0
        val seen = mutableListOf<LiveFeedEvent<String>>()
        val job = launch {
            keepConnected<String>(timeSource = testTimeSource) {
                flow { opened++; emit(SocketEvent.Open); awaitCancellation() }
            }.collect { seen += it }
        }
        advanceTimeBy(10.seconds)
        job.cancel()
        assertEquals(1, opened)
        assertEquals(listOf<LiveFeedEvent<String>>(LiveFeedEvent.Connected), seen)
    }
}
