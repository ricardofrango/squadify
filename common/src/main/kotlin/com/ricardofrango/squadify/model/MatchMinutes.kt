package com.ricardofrango.squadify.model

// Minutes played per player, from the Starting XI and the substitution log. Shared by the server
// (MatchPlayerStatsCalculator, at full time) and the live match console (design 36, while the match
// is running), so the live numbers and the stored ones can never disagree.
//
// Minutes follow the match clock (the unit MatchEvent.minute uses). The first half runs from 0 to
// its end minute; the second half restarts at Match.halfLengthMinutes (the clock jumps back to the
// nominal half length, see elapsedMatchSeconds) and runs to its end. Stoppage time counts: a 25'
// half that ended at 27' gives 27 minutes to someone who played all of it.
//
// Which half an event belongs to comes from MatchEvent.period, recorded by the server when the event
// was logged, not from its minute: first-half stoppage minutes (46', 47') overlap second-half ones.
// A substitution logged during the break is period 1, so it counts at the end of the first half and
// the player coming on plays the whole second half. Events from before `period` existed fall back to
// comparing createdAt with Match.secondHalfStartedAt, which is only approximate (that timestamp moves
// with pause/resume and clock corrections).
//
// Substitutions are unlimited and in any direction: a player can come off and back on any number
// of times. Time on the pitch starts at 0' for a starter, or at a SUBSTITUTION_IN, and stops at a
// SUBSTITUTION_OUT or RED_CARD. State carries over the break.

/** One player's minutes so far, plus what the live console shows about their current spell. */
data class PlayerMinutes(
    val playerId: String,
    val minutes: Int,
    val onPitch: Boolean,
    val sentOff: Boolean,
    // Minutes in the current spell on the pitch (0 when not on it): "on for 18'".
    val stintMinutes: Int,
    // Clock minute they last went off (substituted or sent off): "off since 9'". Null if never off.
    val lastOffMinute: Int?,
    // Started, or came on at some point -- false is "Not played yet".
    val hasPlayed: Boolean
)

/**
 * Each half as a match-clock [start, end] range. [liveMinute] is the running clock's current minute
 * while a half is being played; without it (half time, full time) the stored end minutes are used.
 */
fun matchHalves(match: Match, liveMinute: Int? = null): List<IntRange> {
    val halfLength = match.halfLengthMinutes ?: 0
    return if (match.secondHalfStartedAt == null) {
        val end = liveMinute ?: match.fullTimeMinute ?: match.firstHalfEndMinute ?: halfLength
        listOf(0..maxOf(0, end))
    } else {
        val firstEnd = match.firstHalfEndMinute ?: halfLength
        val secondEnd = liveMinute ?: match.fullTimeMinute ?: (halfLength * 2)
        // A second half that "ended" before its own restart minute (a clock correction gone wrong)
        // contributes 0 minutes rather than a negative range.
        listOf(0..firstEnd, halfLength..maxOf(halfLength, secondEnd))
    }
}

/** Match minutes played so far across the halves (the "54' match" of the full-time table). */
fun elapsedMatchMinutes(halves: List<IntRange>): Int = halves.sumOf { it.last - it.first }

/**
 * Every called-up player's [PlayerMinutes], keyed by player id. [liveMinute]: see [matchHalves].
 * Opponent events and events without a player are ignored.
 */
fun playerMinutes(
    match: Match,
    callUps: List<MatchCallUp>,
    events: List<MatchEvent>,
    liveMinute: Int? = null
): Map<String, PlayerMinutes> {
    val halves = matchHalves(match, liveMinute)
    val relevant = events.filter {
        !it.isOpponent && it.playerId != null &&
            (it.type == EventType.SUBSTITUTION_IN || it.type == EventType.SUBSTITUTION_OUT || it.type == EventType.RED_CARD)
    }
    val byPlayer = relevant.groupBy { it.playerId!! }
    return callUps.associate { callUp ->
        callUp.playerId to minutesFor(callUp.playerId, callUp.isStarting, byPlayer[callUp.playerId].orEmpty(), halves, match.secondHalfStartedAt)
    }
}

private fun minutesFor(
    playerId: String,
    started: Boolean,
    events: List<MatchEvent>,
    halves: List<IntRange>,
    secondHalfStartedAt: kotlin.time.Instant?
): PlayerMinutes {
    val byHalf = events.groupBy { event ->
        val period = event.period
        when {
            halves.size == 1 -> 0
            period != null -> (period - 1).coerceIn(0, 1)
            secondHalfStartedAt == null || event.createdAt < secondHalfStartedAt -> 0
            else -> 1
        }
    }

    var onPitch = started
    var hasPlayed = started
    var sentOff = false
    var total = 0
    var stint = 0
    var lastOff: Int? = null
    halves.forEachIndexed { index, half ->
        var cursor = half.first
        byHalf[index].orEmpty()
            .sortedWith(compareBy<MatchEvent> { it.minute }.thenBy { it.createdAt })
            .forEach { event ->
                val minute = event.minute.coerceIn(half.first, half.last)
                when (event.type) {
                    EventType.SUBSTITUTION_IN -> if (!onPitch && !sentOff) {
                        onPitch = true
                        hasPlayed = true
                        stint = 0
                        cursor = minute
                    }
                    EventType.SUBSTITUTION_OUT, EventType.RED_CARD -> {
                        if (onPitch) {
                            total += minute - cursor
                            onPitch = false
                        }
                        if (event.type == EventType.RED_CARD) sentOff = true
                        lastOff = event.minute
                        stint = 0
                    }
                    else -> Unit
                }
            }
        if (onPitch) {
            total += half.last - cursor
            stint += half.last - cursor
        }
    }
    return PlayerMinutes(
        playerId = playerId,
        minutes = total,
        onPitch = onPitch,
        sentOff = sentOff,
        stintMinutes = if (onPitch) stint else 0,
        lastOffMinute = lastOff,
        hasPlayed = hasPlayed
    )
}

/**
 * The fair share of minutes so far (design 36): match minutes elapsed × players on the pitch ÷
 * players called up -- what everyone would have if time were split evenly.
 */
fun fairShareMinutes(elapsedMinutes: Int, playersOnPitch: Int, calledUp: Int): Double =
    if (calledUp == 0) 0.0 else elapsedMinutes.toDouble() * playersOnPitch / calledUp

/** "Below share": under 60% of the fair share. Not flagged before [FAIR_SHARE_FROM_MINUTE]. */
fun isBelowShare(minutes: Int, share: Double, elapsedMinutes: Int): Boolean =
    elapsedMinutes >= FAIR_SHARE_FROM_MINUTE && minutes < share * 0.6

/** Shares are meaningless in the first minutes: nobody is flagged before this. */
const val FAIR_SHARE_FROM_MINUTE = 5
