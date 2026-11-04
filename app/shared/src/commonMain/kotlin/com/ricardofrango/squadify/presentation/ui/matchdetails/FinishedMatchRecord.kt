package com.ricardofrango.squadify.presentation.ui.matchdetails

import com.ricardofrango.squadify.model.EventType
import com.ricardofrango.squadify.model.MatchDetails
import com.ricardofrango.squadify.model.MatchPlayerStats
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.presentation.ui.livematch.DisplayEvent
import com.ricardofrango.squadify.presentation.ui.livematch.buildDisplayEvents

// A finished match's record on Match Details (TECHNICAL_DECISIONS.md §66): the logic behind its
// Stats and Events tabs, apart from the composables (FinishedMatchBody.kt) so it can be tested.

/** A finished match's tabs, in the bottom bar: General (the report and the details), Stats, Events, Timeline. */
internal enum class FinishedTab { GENERAL, STATS, EVENTS, TIMELINE }

/**
 * The staff see every tab; anyone else General (the details only) and Events (the goals only). A game
 * entered after it was played ([hasTimeline] false) has no Timeline: nothing recorded the pitch.
 */
internal fun finishedTabs(canSeeMatchRecord: Boolean, hasTimeline: Boolean = true): List<FinishedTab> =
    if (canSeeMatchRecord) FinishedTab.entries.filter { hasTimeline || it != FinishedTab.TIMELINE }
    else listOf(FinishedTab.GENERAL, FinishedTab.EVENTS)

/** The Stats tab's columns after the player, in order: the ranking first. */
internal enum class MatchStatColumn { RATING, MINUTES, GOALS, ASSISTS, SHOTS, SHOTS_ON_GOAL, FOULS, YELLOW, RED }

internal data class MatchStatLine(val player: Player, val stats: MatchPlayerStats) {
    fun value(column: MatchStatColumn): Int? = when (column) {
        MatchStatColumn.RATING -> stats.rating
        MatchStatColumn.MINUTES -> stats.minutesPlayed
        MatchStatColumn.GOALS -> stats.goals
        MatchStatColumn.ASSISTS -> stats.assists
        MatchStatColumn.SHOTS -> stats.shots
        MatchStatColumn.SHOTS_ON_GOAL -> stats.shotsOnGoal
        MatchStatColumn.FOULS -> stats.fouls
        MatchStatColumn.YELLOW -> stats.yellowCards
        MatchStatColumn.RED -> stats.redCards
    }
}

/** Who played (started, or was on the pitch at some point) with their line, and who was called up but never came on. */
internal data class MatchStatLines(val played: List<MatchStatLine>, val unused: List<Player>)

internal fun matchStatLines(details: MatchDetails): MatchStatLines {
    val players = details.callUps.associate { it.player.id to it.player }
    val lines = details.playerStats.mapNotNull { stats -> players[stats.playerId]?.let { MatchStatLine(it, stats) } }
    val (played, unused) = lines.partition { it.stats.started || it.stats.minutesPlayed > 0 }
    return MatchStatLines(played, unused.map { it.player }.sortedWith(compareBy({ it.firstName.lowercase() }, { it.lastName.lowercase() })))
}

/** The column the table opens sorted by: the ranking once there are ratings, else the minutes. */
internal fun defaultStatSort(lines: List<MatchStatLine>): MatchStatColumn =
    if (lines.any { it.stats.rating != null }) MatchStatColumn.RATING else MatchStatColumn.MINUTES

/** Highest first on [column], or by name with none; ties by name. A missing rating sorts last. */
internal fun List<MatchStatLine>.sortedByStat(column: MatchStatColumn?): List<MatchStatLine> {
    val byName = compareBy<MatchStatLine>({ it.player.firstName.lowercase() }, { it.player.lastName.lowercase() })
    return if (column == null) sortedWith(byName)
    else sortedWith(compareByDescending<MatchStatLine> { it.value(column) ?: -1 }.then(byName))
}

/** The Events tab's filter chips. */
internal enum class EventFilter { ALL, GOALS, SUBSTITUTIONS, CARDS }

internal fun EventFilter.accepts(event: DisplayEvent): Boolean = when (this) {
    EventFilter.ALL -> true
    EventFilter.GOALS -> event is DisplayEvent.Simple && event.event.type in GoalTypes
    EventFilter.SUBSTITUTIONS -> event is DisplayEvent.Substitution ||
        (event is DisplayEvent.Simple && (event.event.type == EventType.SUBSTITUTION_IN || event.event.type == EventType.SUBSTITUTION_OUT))
    EventFilter.CARDS -> event is DisplayEvent.Simple && (event.event.type == EventType.YELLOW_CARD || event.event.type == EventType.RED_CARD)
}

// An assist logged on its own (before assists were linked to their goal) still reads as part of the goals.
private val GoalTypes = setOf(EventType.GOAL, EventType.OWN_GOAL, EventType.ASSIST)

/** Which half an event row is in (1 for an event from before halves were recorded). */
internal val DisplayEvent.period: Int
    get() = when (this) {
        is DisplayEvent.Simple -> event.period ?: 1
        is DisplayEvent.Substitution -> out.period ?: 1
    }

/** The match's events as rows (a substitution's two halves as one, a goal with its assist), in the order they happened. */
internal fun chronologicalEvents(details: MatchDetails): List<DisplayEvent> =
    buildDisplayEvents(details.events).sortedWith(compareBy<DisplayEvent>({ it.period }, { it.minute }, { it.createdAt }))
