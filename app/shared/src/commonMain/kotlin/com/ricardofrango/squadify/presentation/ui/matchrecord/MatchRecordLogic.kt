package com.ricardofrango.squadify.presentation.ui.matchrecord

import com.ricardofrango.squadify.model.EventType
import com.ricardofrango.squadify.model.MatchDetails
import com.ricardofrango.squadify.model.MatchEvent
import com.ricardofrango.squadify.model.MatchRecordEvent
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.SaveMatchRecordRequest
import com.ricardofrango.squadify.presentation.ui.matchdetails.CallUpSort
import com.ricardofrango.squadify.presentation.ui.matchdetails.playerComparator
import kotlin.time.Instant

// A match's record after the game (TECHNICAL_DECISIONS.md §70): correcting the goals and cards of a
// game followed live, or entering one that wasn't. The rules behind the editor, apart from its
// composables so they can be tested.

/** The half lengths offered, as on the live console. */
internal val RECORD_HALF_LENGTHS = listOf(20, 25, 30, 40, 45)

internal const val DEFAULT_HALF_LENGTH = 25

/** The minutes list opens sorted by name, as Rate players. */
internal val DEFAULT_MINUTES_SORT = CallUpSort.NAME

/** The called-up players in the minutes list, by name or position (the call-up's order, §69). */
internal fun minutesOrder(players: List<Player>, sort: CallUpSort = DEFAULT_MINUTES_SORT): List<Player> =
    players.sortedWith(playerComparator(sort))

/** A finished match's record can be edited by whoever runs its games (any of the age group's staff). */
fun MatchDetails.canEditRecord(): Boolean =
    canManageLiveMatch && canSeeMatchRecord && match.status == MatchStatus.FINISHED

/**
 * A game that wasn't followed live can have its result entered once its kickoff time has passed,
 * from its saved call-up.
 */
fun MatchDetails.canEnterResult(now: Instant): Boolean =
    canManageLiveMatch && match.status == MatchStatus.SCHEDULED && match.dateTime <= now && callUps.isNotEmpty()

/** Entering a game afterwards (minutes typed in), rather than correcting one followed live. */
internal val MatchDetails.isAfterGame: Boolean
    get() = match.status == MatchStatus.SCHEDULED || match.enteredAfterGame

/** What the editor can add: our goals and own goals, the opponent's goals, our cards. */
enum class RecordEventKind { GOAL, OWN_GOAL, YELLOW, RED }

internal val RecordEventKind.type: EventType
    get() = when (this) {
        RecordEventKind.GOAL -> EventType.GOAL
        RecordEventKind.OWN_GOAL -> EventType.OWN_GOAL
        RecordEventKind.YELLOW -> EventType.YELLOW_CARD
        RecordEventKind.RED -> EventType.RED_CARD
    }

/** Goals and own goals can be either side's; cards are only recorded for our players. */
internal val RecordEventKind.hasSide: Boolean get() = this == RecordEventKind.GOAL || this == RecordEventKind.OWN_GOAL

/**
 * One event on the editor. [key] tells them apart on screen ([id] is null for one not saved yet).
 * [assistPlayerId]: who set up one of our goals. [standaloneAssist]: an assist logged on its own,
 * before assists rode on their goal, kept as it is unless deleted.
 */
data class DraftEvent(
    val key: String,
    val id: String?,
    val type: EventType,
    val minute: Int,
    val period: Int,
    val playerId: String?,
    val isOpponent: Boolean,
    val assistPlayerId: String? = null
) {
    val kind: RecordEventKind?
        get() = when (type) {
            EventType.GOAL -> RecordEventKind.GOAL
            EventType.OWN_GOAL -> RecordEventKind.OWN_GOAL
            EventType.YELLOW_CARD -> RecordEventKind.YELLOW
            EventType.RED_CARD -> RecordEventKind.RED
            else -> null
        }

    fun toRequest() = MatchRecordEvent(
        id = id,
        type = type,
        minute = minute,
        period = period,
        playerId = if (isOpponent) null else playerId,
        isOpponent = isOpponent,
        assistPlayerId = if (type == EventType.GOAL && !isOpponent) assistPlayerId else null
    )
}

/** The match's goals, own goals, cards and lone assists as editor rows; substitutions stay out. */
internal fun draftEvents(events: List<MatchEvent>): List<DraftEvent> {
    val assists = events.filter { it.type == EventType.ASSIST && it.relatedEventId != null }.associateBy { it.relatedEventId }
    return events
        .filter { it.type != EventType.SUBSTITUTION_IN && it.type != EventType.SUBSTITUTION_OUT }
        .filterNot { it.type == EventType.ASSIST && it.relatedEventId != null }
        .map {
            DraftEvent(
                key = it.id, id = it.id, type = it.type, minute = it.minute, period = it.period ?: 1,
                playerId = it.playerId, isOpponent = it.isOpponent, assistPlayerId = assists[it.id]?.playerId
            )
        }
        .sortedWith(compareBy({ it.period }, { it.minute }))
}

/** The score the editor's events make: ours first. An own goal counts for the other side. */
internal fun draftScore(events: List<DraftEvent>): Pair<Int, Int> {
    var ours = 0
    var theirs = 0
    events.forEach {
        when (it.type) {
            EventType.GOAL -> if (it.isOpponent) theirs++ else ours++
            EventType.OWN_GOAL -> if (it.isOpponent) ours++ else theirs++
            else -> Unit
        }
    }
    return ours to theirs
}

/**
 * The editor rows as match events, to be drawn like the finished match's Events tab (a goal's assist
 * as its own linked event). [matchId] and [at] only fill the fields the rows don't use.
 */
internal fun List<DraftEvent>.asMatchEvents(matchId: String, at: Instant): List<MatchEvent> = flatMap { draft ->
    val event = MatchEvent(draft.key, matchId, draft.type, draft.minute, draft.playerId, isOpponent = draft.isOpponent, createdAt = at, period = draft.period)
    val assist = draft.assistPlayerId?.takeIf { draft.type == EventType.GOAL && !draft.isOpponent }?.let {
        event.copy(id = "${draft.key}-assist", type = EventType.ASSIST, playerId = it, relatedEventId = draft.key)
    }
    listOfNotNull(event, assist)
}

/** Why the record can't be saved yet; null when it can. */
enum class RecordProblem { MINUTES_INVALID, SCORER_DIDNT_PLAY }

/**
 * Minutes typed in for a game entered afterwards: whole numbers from 0 to the match's length, left
 * empty for 0. A goal or assist by someone with no minutes is refused, as on the server.
 */
internal fun recordProblem(
    afterGame: Boolean,
    events: List<DraftEvent>,
    minutes: Map<String, String>,
    matchLength: Int,
    playedLive: Set<String>
): RecordProblem? {
    val played = if (afterGame) {
        val parsed = minutes.mapValues { (_, text) -> if (text.isBlank()) 0 else text.trim().toIntOrNull() }
        if (parsed.values.any { it == null || it !in 0..matchLength }) return RecordProblem.MINUTES_INVALID
        parsed.filterValues { (it ?: 0) > 0 }.keys
    } else {
        playedLive
    }
    val scorers = events.filter { !it.isOpponent && (it.type == EventType.GOAL || it.type == EventType.OWN_GOAL || it.type == EventType.ASSIST) }
        .mapNotNull { it.playerId } + events.mapNotNull { if (it.type == EventType.GOAL && !it.isOpponent) it.assistPlayerId else null }
    // A match followed live from before stats existed knows nobody's minutes: nothing to check.
    if ((afterGame || playedLive.isNotEmpty()) && scorers.any { it !in played }) return RecordProblem.SCORER_DIDNT_PLAY
    return null
}

/** The request for the editor's state. Minutes only for a game entered afterwards. */
internal fun recordRequest(
    afterGame: Boolean,
    events: List<DraftEvent>,
    halfLength: Int,
    minutes: Map<String, String>
): SaveMatchRecordRequest = SaveMatchRecordRequest(
    events = events.map { it.toRequest() },
    halfLengthMinutes = if (afterGame) halfLength else null,
    minutesPlayed = if (afterGame) {
        minutes.mapNotNull { (id, text) -> text.trim().toIntOrNull()?.let { id to it } }.toMap()
    } else {
        emptyMap()
    }
)
