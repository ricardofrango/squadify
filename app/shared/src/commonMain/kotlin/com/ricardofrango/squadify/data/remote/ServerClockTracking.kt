package com.ricardofrango.squadify.data.remote

import com.ricardofrango.squadify.domain.service.ServerClock
import com.ricardofrango.squadify.model.SERVER_TIME_HEADER
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.api.createClientPlugin
import kotlin.time.Instant

/** Feeds [ServerClock] from the server's time on every response (`SERVER_TIME_HEADER`). */
fun HttpClientConfig<*>.trackServerClock() {
    install(ServerClockPlugin)
}

private val ServerClockPlugin = createClientPlugin("ServerClock") {
    onResponse { response ->
        val stamped = response.headers[SERVER_TIME_HEADER]?.toLongOrNull() ?: return@onResponse
        ServerClock.record(
            serverNow = Instant.fromEpochMilliseconds(stamped),
            requestSent = Instant.fromEpochMilliseconds(response.requestTime.timestamp),
            responseReceived = Instant.fromEpochMilliseconds(response.responseTime.timestamp)
        )
    }
}
