package com.ricardofrango.squadify.domain.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** What one WebSocket connection says: that it's open, then its messages. Its flow ends, or throws, when it drops. */
sealed interface SocketEvent<out T> {
    data object Open : SocketEvent<Nothing>
    data class Message<T>(val value: T) : SocketEvent<T>

    /** The server closed it because the space's plan doesn't follow games live: not to be tried again. */
    data object NotInPlan : SocketEvent<Nothing>
}

/** What a feed kept connected by [keepConnected] says. */
sealed interface LiveFeedEvent<out T> {
    /** The socket is open (again): anything sent while it was down was missed, so reload. */
    data object Connected : LiveFeedEvent<Nothing>

    /** The socket dropped (or couldn't open); it's tried again shortly. */
    data object Disconnected : LiveFeedEvent<Nothing>

    data class Update<T>(val value: T) : LiveFeedEvent<T>

    /** The server says the space's plan doesn't follow games live; the feed ends here, untried again. */
    data object NotInPlan : LiveFeedEvent<Nothing>
}

/** How long to wait before each new try: 1s, 2s, 5s, then every 15s. */
val LIVE_FEED_RETRY_DELAYS: List<Duration> = listOf(1.seconds, 2.seconds, 5.seconds, 15.seconds)

/**
 * A live socket that reopens itself whenever it drops (the phone locked, a change of network, the
 * server restarting), for as long as it's collected. Before, a drop was final until the screen was
 * reopened, so a watcher's screen silently stopped updating mid-game. [connect] opens one connection.
 * The waits ([retryDelays]) start again from the first once a connection has stayed open for
 * [stableAfter], so a socket the server closes straight away (an expired token) isn't retried every second.
 * A socket the server refuses for the plan ([SocketEvent.NotInPlan]) ends the feed: the server decides
 * what each plan has, so the app always asks and takes no for an answer.
 */
fun <T> keepConnected(
    retryDelays: List<Duration> = LIVE_FEED_RETRY_DELAYS,
    stableAfter: Duration = 30.seconds,
    timeSource: TimeSource = TimeSource.Monotonic,
    connect: () -> Flow<SocketEvent<T>>
): Flow<LiveFeedEvent<T>> = flow {
    var failures = 0
    while (true) {
        var openedAt: TimeMark? = null
        var notInPlan = false
        try {
            connect().collect { event ->
                when (event) {
                    SocketEvent.Open -> {
                        openedAt = timeSource.markNow()
                        emit(LiveFeedEvent.Connected)
                    }
                    is SocketEvent.Message -> emit(LiveFeedEvent.Update(event.value))
                    SocketEvent.NotInPlan -> notInPlan = true
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Dropped or refused: tried again below.
        }
        if (notInPlan) {
            emit(LiveFeedEvent.NotInPlan)
            return@flow
        }
        if (openedAt?.let { it.elapsedNow() >= stableAfter } == true) failures = 0
        emit(LiveFeedEvent.Disconnected)
        delay(retryDelays[failures.coerceAtMost(retryDelays.lastIndex)])
        failures++
    }
}
