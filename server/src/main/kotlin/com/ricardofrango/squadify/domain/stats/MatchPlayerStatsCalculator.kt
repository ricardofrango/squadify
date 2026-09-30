package com.ricardofrango.squadify.domain.stats

import com.ricardofrango.squadify.model.EventType
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchCallUp
import com.ricardofrango.squadify.model.MatchEvent
import com.ricardofrango.squadify.model.MatchPlayerStats

/**
 * Builds each called-up player's [MatchPlayerStats] for a match that has just finished. Pure -- no
 * repositories -- so the minutes-played rules can be unit tested directly.
 *
 * Minutes follow the match clock (the unit MatchEvent.minute uses). The first half runs from 0 to
 * [Match.firstHalfEndMinute]; the second half restarts at [Match.halfLengthMinutes] (the clock jumps
 * back to the nominal half length, see elapsedMatchSeconds) and runs to [Match.fullTimeMinute]. So a
 * 45-minute half that ended at 47' and a second half that ended at 93' gives 47 + 48 = 95 minutes for
 * someone who played the whole match.
 *
 * Which half an event belongs to is decided by when it was logged ([MatchEvent.createdAt] before or
 * after [Match.secondHalfStartedAt]), not by its minute: first-half stoppage-time minutes (46', 47')
 * overlap second-half ones. A substitution logged during the break counts as happening at the end of
 * the first half, so the player coming on plays the whole second half.
 *
 * A player's time on the pitch starts at 0' if they started, or at their SUBSTITUTION_IN, and stops at
 * their SUBSTITUTION_OUT or RED_CARD. Their state carries over the break.
 */
object MatchPlayerStatsCalculator {

    fun calculate(match: Match, callUps: List<MatchCallUp>, events: List<MatchEvent>): List<MatchPlayerStats> {
        val halves = halves(match)
        val secondHalfStartedAt = match.secondHalfStartedAt
        val eventsByPlayer = events.filter { !it.isOpponent && it.playerId != null }.groupBy { it.playerId!! }

        return callUps.map { callUp ->
            val own = eventsByPlayer[callUp.playerId].orEmpty()
            MatchPlayerStats(
                matchId = match.id,
                playerId = callUp.playerId,
                started = callUp.isStarting,
                minutesPlayed = minutesPlayed(callUp.isStarting, own, halves, secondHalfStartedAt),
                goals = own.count { it.type == EventType.GOAL },
                assists = own.count { it.type == EventType.ASSIST },
                ownGoals = own.count { it.type == EventType.OWN_GOAL },
                yellowCards = own.count { it.type == EventType.YELLOW_CARD },
                redCards = own.count { it.type == EventType.RED_CARD }
            )
        }
    }

    /** Each half as a match-clock [start, end] range. Only one range if the second half never started. */
    private fun halves(match: Match): List<IntRange> {
        val halfLength = match.halfLengthMinutes ?: 0
        return if (match.secondHalfStartedAt == null) {
            val end = match.fullTimeMinute ?: match.firstHalfEndMinute ?: halfLength
            listOf(0..end)
        } else {
            val firstEnd = match.firstHalfEndMinute ?: halfLength
            val secondEnd = match.fullTimeMinute ?: (halfLength * 2)
            // Guard against a second half that ended before its own restart minute (a clock correction
            // gone wrong) -- it contributes 0 minutes rather than a negative range.
            listOf(0..firstEnd, halfLength..maxOf(halfLength, secondEnd))
        }
    }

    private fun minutesPlayed(
        started: Boolean,
        events: List<MatchEvent>,
        halves: List<IntRange>,
        secondHalfStartedAt: kotlin.time.Instant?
    ): Int {
        val relevant = events.filter {
            it.type == EventType.SUBSTITUTION_IN || it.type == EventType.SUBSTITUTION_OUT || it.type == EventType.RED_CARD
        }
        val byHalf = relevant.groupBy { event ->
            if (halves.size == 1 || secondHalfStartedAt == null || event.createdAt < secondHalfStartedAt) 0 else 1
        }

        var onPitch = started
        var total = 0
        halves.forEachIndexed { index, half ->
            var cursor = half.first
            byHalf[index].orEmpty()
                .sortedWith(compareBy<MatchEvent> { it.minute }.thenBy { it.createdAt })
                .forEach { event ->
                    val minute = event.minute.coerceIn(half.first, half.last)
                    when (event.type) {
                        EventType.SUBSTITUTION_IN -> if (!onPitch) {
                            onPitch = true
                            cursor = minute
                        }
                        EventType.SUBSTITUTION_OUT, EventType.RED_CARD -> if (onPitch) {
                            total += minute - cursor
                            onPitch = false
                        }
                        else -> Unit
                    }
                }
            if (onPitch) total += half.last - cursor
        }
        return total
    }
}
