package com.ricardofrango.squadify.model

import kotlinx.serialization.Serializable

// The live pitch view (REQUIREMENTS, Phase 5 "Live pitch view"). A formation is "D-M-A": defenders,
// midfielders, attackers, which with the goalkeeper make the team size ("2-3-1" in 7-a-side). The
// pitch has one position ("slot") per player: slot 0 is the goalkeeper, then the defence, midfield
// and attack, each zone left to right. Changing formation keeps every player's slot number, so going
// from 2-3-1 to 1-3-2 moves slot 2 (the second defender) into midfield and slot 5 (the last
// midfielder) up front -- moving players to their right positions is the coach's job.

/** A match's arrangement: its formation and who stands in each slot (slot -> player id). */
@Serializable
data class MatchLineup(
    val formation: String,
    val slots: Map<Int, String> = emptyMap()
)

/**
 * Body of `PUT /matches/{matchId}/lineup` -- the whole arrangement, replacing the saved one. Before
 * kickoff the slotted players are the starters; during the game they must be on the pitch.
 */
@Serializable
data class SetMatchLineupRequest(
    val formation: String,
    val slots: Map<Int, String>
)

/** "2-3-1" -> [2, 3, 1], when it's three whole numbers that with the goalkeeper make [teamSize]. */
fun parseFormation(text: String, teamSize: Int): List<Int>? {
    val parts = text.trim().split("-")
    if (parts.size != 3) return null
    val numbers = parts.map { part -> part.trim().takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toIntOrNull() ?: return null }
    return numbers.takeIf { it.sum() == teamSize - 1 }
}

/** Each team size's formation until the coach picks another: 1-2-1, 2-3-1, 2-4-2 and 4-4-2. */
fun defaultFormation(teamSize: Int): List<Int> = when (teamSize) {
    5 -> listOf(1, 2, 1)
    7 -> listOf(2, 3, 1)
    9 -> listOf(2, 4, 2)
    11 -> listOf(4, 4, 2)
    else -> {
        // Any other size (a sport with other team sizes): about a quarter up front, the rest split
        // between defence and midfield.
        val outfield = (teamSize - 1).coerceAtLeast(0)
        val attack = if (outfield >= 3) maxOf(1, outfield / 4) else 0
        val defence = (outfield - attack + 1) / 2
        listOf(defence, outfield - attack - defence, attack)
    }
}

fun List<Int>.formationText(): String = joinToString("-")

/** The formation a match plays in: its saved one when it fits the team, else the team size's default. */
fun matchFormation(saved: String?, teamSize: Int): List<Int> =
    saved?.let { parseFormation(it, teamSize) } ?: defaultFormation(teamSize)

/** Where a slot stands: its [zone] (0 goalkeeper, 1 defence, 2 midfield, 3 attack), its [index] in that zone, of [count]. */
data class PitchSpot(val zone: Int, val index: Int, val count: Int)

fun pitchSpot(formation: List<Int>, slot: Int): PitchSpot {
    if (slot == 0) return PitchSpot(0, 0, 1)
    var rest = slot - 1
    formation.forEachIndexed { zone, count ->
        if (rest < count) return PitchSpot(zone + 1, rest, count)
        rest -= count
    }
    throw IllegalArgumentException("Slot $slot is outside the formation ${formation.formationText()}")
}

/**
 * Places [players] (in their listed order) by their positions, for a match with no saved arrangement
 * (one kicked off before the pitch view): a goalkeeper in goal, defenders in defence, midfielders and
 * wingers in midfield, strikers up front; whoever doesn't fit takes the first free slots.
 */
fun autoArrangeSlots(formation: List<Int>, players: List<Pair<String, PlayerPosition?>>): Map<Int, String> {
    val size = formation.sum() + 1
    val slots = mutableMapOf<Int, String>()
    fun zoneOf(position: PlayerPosition?) = when (position) {
        PlayerPosition.GOALKEEPER -> 0
        PlayerPosition.DEFENDER -> 1
        PlayerPosition.MIDFIELD, PlayerPosition.WINGER -> 2
        PlayerPosition.STRIKER -> 3
        null -> null
    }
    val leftover = mutableListOf<String>()
    for ((id, position) in players) {
        val zone = zoneOf(position)
        val free = (0 until size).firstOrNull { it !in slots && zone != null && pitchSpot(formation, it).zone == zone }
        if (free != null) slots[free] = id else leftover += id
    }
    val free = (0 until size).filter { it !in slots }.iterator()
    for (id in leftover) if (free.hasNext()) slots[free.next()] = id
    return slots
}

/**
 * Who stands where right now. [stored] is the arrangement the coach last saved, [onPitch] who is on
 * the pitch now (from the starters and the events, see playerMinutes). A substitution logged since that
 * arrangement puts the player coming on in the slot of the one going off; anyone gone off (a red
 * card) leaves their slot empty; and anyone on the pitch still without a slot takes the first free one.
 * Every device works this out from the same saved data, so they all show the same pitch.
 */
fun currentPitchSlots(teamSize: Int, stored: Map<Int, String>, onPitch: Set<String>, events: List<MatchEvent>): Map<Int, String> {
    val slots = stored.filterKeys { it in 0 until teamSize }.toMutableMap()
    // A substitution is a SUBSTITUTION_OUT and a SUBSTITUTION_IN logged together at the same minute.
    val outs = events.filter { it.type == EventType.SUBSTITUTION_OUT && !it.isOpponent && it.playerId != null }
        .sortedWith(compareBy<MatchEvent> { it.minute }.thenBy { it.createdAt })
    val ins = events.filter { it.type == EventType.SUBSTITUTION_IN && !it.isOpponent && it.playerId != null }
        .sortedBy { it.createdAt }.toMutableList()
    for (out in outs) {
        val into = ins.firstOrNull { it.minute == out.minute } ?: continue
        ins.remove(into)
        val incoming = into.playerId!!
        if (incoming in slots.values) continue
        val slot = slots.entries.firstOrNull { it.value == out.playerId }?.key ?: continue
        slots[slot] = incoming
    }
    slots.entries.removeAll { it.value !in onPitch }
    val free = (0 until teamSize).filter { it !in slots }.iterator()
    onPitch.filter { it !in slots.values }.sorted().forEach { if (free.hasNext()) slots[free.next()] = it }
    return slots
}

/**
 * One arrangement of the pitch during a game, kept so a finished match can be replayed (its Timeline
 * tab): the starting one at kickoff, then each formation change or position swap the coach saved
 * (SetMatchLineupUseCase). [minute] is the match clock when it was saved and [period] its half, the
 * same as an event's. Substitutions and red cards aren't stored here; they're the match's events.
 */
@Serializable
data class MatchLineupChange(
    val minute: Int,
    val period: Int,
    val formation: String,
    val slots: Map<Int, String>,
    val createdAt: kotlin.time.Instant
)
