package com.ricardofrango.squadify.presentation.ui.livematch

import com.ricardofrango.squadify.model.MatchDetails
import com.ricardofrango.squadify.model.MatchEvent
import com.ricardofrango.squadify.model.MatchLineup
import com.ricardofrango.squadify.model.autoArrangeSlots
import com.ricardofrango.squadify.model.currentPitchSlots
import com.ricardofrango.squadify.model.formationText
import com.ricardofrango.squadify.model.matchFormation

// The live pitch view's arrangement, read from and written onto MatchDetails: the formation is on the
// match and each player's position on their call-up (see Formation.kt), so one refetch or one socket
// update keeps both in step.

/** How many a side this match is played (Match.teamSize). */
internal val MatchDetails.teamSize: Int get() = match.teamSize

/** The formation the match plays in now: its saved one, or the team size's default. */
internal fun MatchDetails.formation(): List<Int> = matchFormation(match.formation, teamSize)

/** Slot -> player id, as the coach last saved it. */
internal fun MatchDetails.storedSlots(): Map<Int, String> =
    callUps.mapNotNull { c -> c.callUp.pitchSlot?.let { it to c.callUp.playerId } }.toMap()

internal fun MatchDetails.storedLineup() = MatchLineup(formation().formationText(), storedSlots())

/** The match played [size] a side. */
internal fun MatchDetails.withTeamSize(size: Int): MatchDetails = copy(match = match.copy(teamSize = size))

internal fun MatchDetails.withLineup(lineup: MatchLineup): MatchDetails {
    val slotOf = lineup.slots.entries.associate { (slot, playerId) -> playerId to slot }
    return copy(
        match = match.copy(formation = lineup.formation),
        callUps = callUps.map { it.copy(callUp = it.callUp.copy(pitchSlot = slotOf[it.callUp.playerId])) }
    )
}

/**
 * Who stands where right now. Before kickoff that's the saved arrangement. During the game it also
 * takes in the substitutions and red cards since (currentPitchSlots). A match kicked off before the
 * pitch view has no arrangement, so its starters are placed by their positions.
 */
internal fun MatchDetails.currentSlots(events: List<MatchEvent>, onPitch: Set<String>): Map<Int, String> {
    val stored = storedSlots()
    if (match.kickoffAt == null) return stored
    val base = stored.ifEmpty {
        autoArrangeSlots(formation(), callUps.filter { it.callUp.isStarting }.map { it.callUp.playerId to it.player.position })
    }
    return currentPitchSlots(teamSize, base, onPitch, events)
}

// Rearranging slots (slot -> player id). Each returns a new arrangement to save.

/** The players in [a] and [b] trade places; either may be empty, which makes it a move. */
internal fun Map<Int, String>.swapped(a: Int, b: Int): Map<Int, String> {
    val next = toMutableMap()
    val inA = next.remove(a)
    val inB = next.remove(b)
    inA?.let { next[b] = it }
    inB?.let { next[a] = it }
    return next
}

/** [playerId] stands in [slot] (leaving wherever they were); whoever stood there goes to the bench. */
internal fun Map<Int, String>.placed(slot: Int, playerId: String): Map<Int, String> =
    filterValues { it != playerId } + (slot to playerId)

/** The first empty position, or null when the pitch is full. */
internal fun Map<Int, String>.firstFreeSlot(teamSize: Int): Int? = (0 until teamSize).firstOrNull { it !in this }
