package com.ricardofrango.squadify.model

import kotlinx.serialization.Serializable

// The live game's shots, fouls and corners (the developer, 2026-11-03), counted as in football: a goal is
// also a shot on goal, and a shot on goal also a shot, so the coach records each attempt once, with how it
// ended. Shots are only recorded for our players, so the opponent's are unknown; fouls are ours (by a
// player) and the opponent's (as a team); corners are each side's. Shared by the server (the players'
// stats lines, the season records) and the app (a match's Stats tab), so they can't disagree.

/** One player's shots, shots on goal and fouls in [events] (their own, as in MatchPlayerStats). */
data class PlayerTallies(val shots: Int, val shotsOnGoal: Int, val fouls: Int)

fun playerTallies(events: List<MatchEvent>): PlayerTallies {
    val goals = events.count { it.type == EventType.GOAL }
    val onGoal = events.count { it.type == EventType.SHOT_ON_GOAL } + goals
    return PlayerTallies(
        shots = events.count { it.type == EventType.SHOT } + onGoal,
        shotsOnGoal = onGoal,
        fouls = events.count { it.type == EventType.FOUL }
    )
}

/** One side's totals in a match; [shots] and [shotsOnGoal] are null for the opponent (never recorded). */
@Serializable
data class SideTallies(val shots: Int?, val shotsOnGoal: Int?, val corners: Int, val fouls: Int)

/** Both sides' totals in a match. [recorded]: anything beyond goals was logged (older matches have none). */
@Serializable
data class MatchTallies(val ours: SideTallies, val theirs: SideTallies, val recorded: Boolean)

private val TALLY_TYPES = setOf(EventType.SHOT, EventType.SHOT_ON_GOAL, EventType.FOUL, EventType.CORNER)

fun matchTallies(events: List<MatchEvent>): MatchTallies {
    val (theirEvents, ourEvents) = events.partition { it.isOpponent }
    // Our goals count as shots on goal whoever scored them (a goal logged without the scorer too).
    val ours = playerTallies(ourEvents)
    return MatchTallies(
        ours = SideTallies(ours.shots, ours.shotsOnGoal, ourEvents.count { it.type == EventType.CORNER }, ours.fouls),
        theirs = SideTallies(null, null, theirEvents.count { it.type == EventType.CORNER }, theirEvents.count { it.type == EventType.FOUL }),
        recorded = events.any { it.type in TALLY_TYPES }
    )
}

/** A shot, shot on goal, foul or corner: recorded in the live game, never changing the score or the pitch. */
fun EventType.isTally(): Boolean = this in TALLY_TYPES
