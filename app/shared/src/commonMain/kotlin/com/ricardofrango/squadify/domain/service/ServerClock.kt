package com.ricardofrango.squadify.domain.service

import com.ricardofrango.squadify.model.estimateServerClockOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * The server's clock as this device best knows it. Every match timestamp is the server's, so a live
 * minute counted on a device a few seconds off would disagree with the other devices; each response
 * says the server's time (`SERVER_TIME_HEADER`, read by `trackServerClock`) and refreshes the
 * estimate. Before any response it's the device's own clock.
 */
object ServerClock {
    private val _offset = MutableStateFlow(Duration.ZERO)

    /** How far the server's clock is ahead of the device's (negative if behind). */
    val offset: StateFlow<Duration> = _offset.asStateFlow()

    fun now(): Instant = Clock.System.now() + _offset.value

    /** One response's stamp: the server answered at [serverNow], sent at [requestSent] and received at [responseReceived] (device times). */
    fun record(serverNow: Instant, requestSent: Instant, responseReceived: Instant) {
        _offset.value = estimateServerClockOffset(serverNow, requestSent, responseReceived)
    }
}
