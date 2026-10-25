package com.ricardofrango.squadify.model

// A finished match's Timeline tab (its staff view): the pitch at every point the team on it changed.
// The first entry is the side that kicked off; each later one is a substitution, a player sent off,
// or an arrangement the coach saved during the game (a new formation, or players swapping positions).
// Goals, assists and yellow cards don't move anyone, so they aren't entries.
//
// The arrangements are the match's MatchLineupChange rows; substitutions and red cards come from its
// events, applied the way the live pitch applies them (currentPitchSlots): the player coming on takes
// the place of the one going off, and a player sent off leaves their place empty. Everything is put in
// the order it was logged (createdAt), which is the order the coach did it in.

enum class TimelineChange { KICKOFF, SUBSTITUTION, SENT_OFF, FORMATION, POSITIONS }

/**
 * The pitch after one change: [slots] (slot -> player id) in [formation], the [bench] (called up, off
 * the pitch, in call-up order) and anyone [sentOff]. [playerIn] / [playerOut] are who a substitution
 * brought on and took off; [playerOut] alone is who a red card sent off.
 */
data class TimelineEntry(
    val change: TimelineChange,
    val minute: Int,
    val period: Int,
    val formation: List<Int>,
    val slots: Map<Int, String>,
    val bench: List<String>,
    val sentOff: List<String>,
    val playerIn: String? = null,
    val playerOut: String? = null
)

/**
 * Every [TimelineEntry] of a match, kickoff first. [positions] are the called-up players' positions,
 * only used to place the starters of a match kicked off before arrangements were kept (no
 * [changes] from kickoff): they then stand by their positions in the starting formation.
 *
 * Arrangements saved at the same minute with nothing else in between (a few quick swaps) are one entry.
 */
fun matchTimeline(
    teamSize: Int,
    startingFormation: String?,
    callUps: List<MatchCallUp>,
    positions: Map<String, PlayerPosition?>,
    events: List<MatchEvent>,
    changes: List<MatchLineupChange>
): List<TimelineEntry> {
    val calledUp = callUps.map { it.playerId }
    val starters = callUps.filter { it.isStarting }.map { it.playerId }
    val sortedChanges = changes.sortedBy { it.createdAt }
    // The arrangement saved at kickoff, when there is one (StartMatchUseCase keeps it).
    val kickoff = sortedChanges.firstOrNull()?.takeIf { it.minute == 0 && it.period == 1 && it.slots.values.toSet() == starters.toSet() }

    var formation = kickoff?.let { parseFormation(it.formation, teamSize) } ?: matchFormation(startingFormation, teamSize)
    var slots: Map<Int, String> = kickoff?.slots?.filterKeys { it in 0 until teamSize }
        ?: autoArrangeSlots(formation, starters.map { it to positions[it] })
    val sentOff = mutableListOf<String>()

    fun entry(change: TimelineChange, minute: Int, period: Int, playerIn: String? = null, playerOut: String? = null) = TimelineEntry(
        change, minute, period, formation, slots,
        bench = calledUp.filter { it !in slots.values && it !in sentOff },
        sentOff = sentOff.toList(),
        playerIn = playerIn, playerOut = playerOut
    )

    val entries = mutableListOf(entry(TimelineChange.KICKOFF, 0, 1))

    fun substitute(out: MatchEvent, into: MatchEvent) {
        val outId = out.playerId!!
        val inId = into.playerId!!
        val slot = slots.entries.firstOrNull { it.value == outId }?.key ?: return
        if (inId in slots.values || inId in sentOff) return
        slots = slots + (slot to inId)
        entries += entry(TimelineChange.SUBSTITUTION, out.minute, out.period ?: 1, playerIn = inId, playerOut = outId)
    }

    fun sendOff(red: MatchEvent) {
        val id = red.playerId!!
        if (id in sentOff) return
        val wasOn = id in slots.values
        sentOff += id
        slots = slots.filterValues { it != id }
        if (wasOn) entries += entry(TimelineChange.SENT_OFF, red.minute, red.period ?: 1, playerOut = id)
    }

    fun rearrange(change: MatchLineupChange) {
        val newFormation = parseFormation(change.formation, teamSize) ?: formation
        // Only who's on the pitch now can stand on it; anyone on it without a place takes a free one.
        val onPitch = slots.values.toSet()
        val placed = change.slots.filter { (slot, id) -> slot in 0 until teamSize && id in onPitch }.toMutableMap()
        val free = (0 until teamSize).filter { it !in placed }.iterator()
        onPitch.filter { it !in placed.values }.sorted().forEach { if (free.hasNext()) placed[free.next()] = it }
        if (newFormation == formation && placed == slots) return

        // Quick saves at the same minute, nothing in between, are one entry: it replaces the last.
        val last = entries.last()
        val merging = (last.change == TimelineChange.FORMATION || last.change == TimelineChange.POSITIONS) &&
            last.minute == change.minute && last.period == change.period
        val before = if (merging) entries[entries.size - 2] else last
        if (merging) entries.removeAt(entries.size - 1)
        formation = newFormation
        slots = placed
        if (merging && formation == before.formation && slots == before.slots) return
        entries += entry(if (formation != before.formation) TimelineChange.FORMATION else TimelineChange.POSITIONS, change.minute, change.period)
    }

    // What happened after kickoff, in the order it was logged.
    val steps = mutableListOf<Step>()
    val ours = events.filter { !it.isOpponent && it.playerId != null }
    val outs = ours.filter { it.type == EventType.SUBSTITUTION_OUT }.sortedWith(compareBy<MatchEvent> { it.minute }.thenBy { it.createdAt })
    val ins = ours.filter { it.type == EventType.SUBSTITUTION_IN }.sortedBy { it.createdAt }.toMutableList()
    // A substitution is a SUBSTITUTION_OUT and a SUBSTITUTION_IN logged together at the same minute.
    for (out in outs) {
        val into = ins.firstOrNull { it.minute == out.minute } ?: continue
        ins.remove(into)
        steps += Step.Substitution(out, into)
    }
    ours.filter { it.type == EventType.RED_CARD }.forEach { steps += Step.SentOff(it) }
    sortedChanges.filter { it !== kickoff }.forEach { steps += Step.Rearranged(it) }
    steps.sortedBy { it.at }.forEach { step ->
        when (step) {
            is Step.Substitution -> substitute(step.out, step.into)
            is Step.SentOff -> sendOff(step.red)
            is Step.Rearranged -> rearrange(step.change)
        }
    }
    return entries
}

private sealed class Step(val at: kotlin.time.Instant) {
    class Substitution(val out: MatchEvent, val into: MatchEvent) : Step(out.createdAt)
    class SentOff(val red: MatchEvent) : Step(red.createdAt)
    class Rearranged(val change: MatchLineupChange) : Step(change.createdAt)
}
