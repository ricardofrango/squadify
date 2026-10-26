package com.ricardofrango.squadify.data.remote

import com.ricardofrango.squadify.domain.service.ServerClock
import com.ricardofrango.squadify.model.SERVER_TIME_HEADER
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

// The live badge on match lists counts on the server's clock, learnt from every response.
class ServerClockTrackingTest {

    private fun clientAnsweringWith(serverTime: () -> String?) = HttpClient(MockEngine) {
        engine {
            addHandler {
                val stamp = serverTime()
                respond("{}", headers = if (stamp != null) headersOf(SERVER_TIME_HEADER, stamp) else headersOf())
            }
        }
        trackServerClock()
    }

    @Test
    fun aServerAnHourAheadMovesTheClockAnHourAhead() = runTest {
        clientAnsweringWith { (Clock.System.now() + 1.hours).toEpochMilliseconds().toString() }.get("https://server.test/x")

        assertTrue(abs((ServerClock.offset.value - 1.hours).inWholeMilliseconds) < 5.seconds.inWholeMilliseconds, "offset ${ServerClock.offset.value}")
        assertTrue(abs((ServerClock.now() - Clock.System.now() - 1.hours).inWholeMilliseconds) < 5.seconds.inWholeMilliseconds)
    }

    @Test
    fun aResponseWithoutTheStampLeavesTheEstimateAlone() = runTest {
        clientAnsweringWith { (Clock.System.now() - 2.hours).toEpochMilliseconds().toString() }.get("https://server.test/x")
        val before = ServerClock.offset.value

        clientAnsweringWith { null }.get("https://server.test/y")
        clientAnsweringWith { "not a number" }.get("https://server.test/z")

        assertTrue(ServerClock.offset.value == before)
    }
}
