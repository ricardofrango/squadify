package com.ricardofrango.squadify.domain.stats

import com.ricardofrango.squadify.model.EventType
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchCallUp
import com.ricardofrango.squadify.model.MatchEvent
import com.ricardofrango.squadify.model.MatchPlayerStats
import com.ricardofrango.squadify.model.playerMinutes

/**
 * Builds each called-up player's [MatchPlayerStats] for a match that has just finished. Pure -- no
 * repositories -- so the rules can be unit tested directly. Minutes come from [playerMinutes] in
 * `common/.../model/MatchMinutes.kt`, the same function the live match console uses while the match
 * is running, so the live numbers and the stored ones can never disagree (see that file for the rules:
 * match-clock minutes including stoppage time, halves by MatchEvent.period, unlimited substitutions).
 */
object MatchPlayerStatsCalculator {

    fun calculate(match: Match, callUps: List<MatchCallUp>, events: List<MatchEvent>): List<MatchPlayerStats> {
        val minutes = playerMinutes(match, callUps, events)
        val eventsByPlayer = events.filter { !it.isOpponent && it.playerId != null }.groupBy { it.playerId!! }

        return callUps.map { callUp ->
            val own = eventsByPlayer[callUp.playerId].orEmpty()
            MatchPlayerStats(
                matchId = match.id,
                playerId = callUp.playerId,
                started = callUp.isStarting,
                minutesPlayed = minutes[callUp.playerId]?.minutes ?: 0,
                goals = own.count { it.type == EventType.GOAL },
                assists = own.count { it.type == EventType.ASSIST },
                ownGoals = own.count { it.type == EventType.OWN_GOAL },
                yellowCards = own.count { it.type == EventType.YELLOW_CARD },
                redCards = own.count { it.type == EventType.RED_CARD }
            )
        }
    }
}
