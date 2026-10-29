package com.ricardofrango.squadify.model

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class MatchTimelineTest {

    // A 5-a-side team (1-2-1): five starters a..e and two on the bench, f and g.
    private val callUps = listOf("a", "b", "c", "d", "e", "f", "g").mapIndexed { i, id ->
        MatchCallUp(id = "cu-$id", matchId = "m", playerId = id, status = CallUpStatus.CALLED_UP, isStarting = i < 5)
    }
    private val kickoffSlots = mapOf(0 to "a", 1 to "b", 2 to "c", 3 to "d", 4 to "e")

    private fun at(seconds: Int) = Instant.fromEpochSeconds(1_000_000L + seconds)

    private fun change(seconds: Int, minute: Int, formation: String, slots: Map<Int, String>, period: Int = 1) =
        MatchLineupChange(minute, period, formation, slots, at(seconds))

    private fun event(seconds: Int, type: EventType, minute: Int, playerId: String?, isOpponent: Boolean = false, period: Int = 1) =
        MatchEvent("e$seconds-$type", "m", type, minute, playerId = playerId, isOpponent = isOpponent, createdAt = at(seconds), period = period)

    private fun timeline(events: List<MatchEvent> = emptyList(), changes: List<MatchLineupChange>, positions: Map<String, PlayerPosition?> = emptyMap()) =
        matchTimeline(5, "1-2-1", callUps, positions, events, changes)

    private fun substitution(seconds: Int, minute: Int, out: String, into: String, period: Int = 1) = listOf(
        event(seconds, EventType.SUBSTITUTION_OUT, minute, out, period = period),
        event(seconds + 1, EventType.SUBSTITUTION_IN, minute, into, period = period)
    )

    @Test
    fun theFirstEntryIsTheSideThatKickedOff() {
        val entries = timeline(changes = listOf(change(0, 0, "1-2-1", kickoffSlots)))
        assertEquals(1, entries.size)
        val start = entries.single()
        assertEquals(TimelineChange.KICKOFF, start.change)
        assertEquals(listOf(1, 2, 1), start.formation)
        assertEquals(kickoffSlots, start.slots)
        assertEquals(listOf("f", "g"), start.bench)
    }

    @Test
    fun aSubstitutionPutsThePlayerComingOnInThePlaceOfTheOneGoingOff() {
        val entries = timeline(
            events = substitution(100, 12, out = "c", into = "f"),
            changes = listOf(change(0, 0, "1-2-1", kickoffSlots))
        )
        val sub = entries.last()
        assertEquals(TimelineChange.SUBSTITUTION, sub.change)
        assertEquals(12, sub.minute)
        assertEquals("f", sub.slots[2])
        assertEquals("f", sub.playerIn)
        assertEquals("c", sub.playerOut)
        assertEquals(listOf("c", "g"), sub.bench)
    }

    @Test
    fun goalsAssistsAndYellowCardsMoveNobody() {
        val entries = timeline(
            events = listOf(
                event(50, EventType.GOAL, 5, "e"),
                event(51, EventType.ASSIST, 5, "d"),
                event(60, EventType.YELLOW_CARD, 7, "b"),
                event(70, EventType.GOAL, 9, null, isOpponent = true)
            ),
            changes = listOf(change(0, 0, "1-2-1", kickoffSlots))
        )
        assertEquals(listOf(TimelineChange.KICKOFF), entries.map { it.change })
    }

    @Test
    fun aRedCardLeavesThePlayersPlaceEmpty() {
        val entries = timeline(
            events = listOf(event(100, EventType.RED_CARD, 20, "d")),
            changes = listOf(change(0, 0, "1-2-1", kickoffSlots))
        )
        val red = entries.last()
        assertEquals(TimelineChange.SENT_OFF, red.change)
        assertNull(red.slots[3])
        assertEquals(listOf("d"), red.sentOff)
        assertEquals("d", red.playerOut)
        assertEquals(listOf("f", "g"), red.bench, "someone sent off isn't on the bench either")
    }

    @Test
    fun aNewFormationAndASwapOfPositionsAreTheirOwnEntries() {
        val entries = timeline(
            changes = listOf(
                change(0, 0, "1-2-1", kickoffSlots),
                change(100, 10, "2-1-1", kickoffSlots),
                change(200, 18, "2-1-1", kickoffSlots + mapOf(1 to "c", 2 to "b"))
            )
        )
        assertEquals(listOf(TimelineChange.KICKOFF, TimelineChange.FORMATION, TimelineChange.POSITIONS), entries.map { it.change })
        assertEquals(listOf(2, 1, 1), entries[1].formation)
        assertEquals("c", entries[2].slots[1])
    }

    @Test
    fun quickSavesAtTheSameMinuteAreOneEntry() {
        val entries = timeline(
            changes = listOf(
                change(0, 0, "1-2-1", kickoffSlots),
                change(100, 10, "1-2-1", kickoffSlots + mapOf(1 to "c", 2 to "b")),
                change(105, 10, "1-2-1", kickoffSlots + mapOf(1 to "c", 2 to "b", 3 to "e", 4 to "d"))
            )
        )
        assertEquals(2, entries.size)
        assertEquals("d", entries[1].slots[4])
        assertEquals(TimelineChange.POSITIONS, entries[1].change)
    }

    @Test
    fun swapsThatUndoEachOtherLeaveNoEntry() {
        val entries = timeline(
            changes = listOf(
                change(0, 0, "1-2-1", kickoffSlots),
                change(100, 10, "1-2-1", kickoffSlots + mapOf(1 to "c", 2 to "b")),
                change(105, 10, "1-2-1", kickoffSlots)
            )
        )
        assertEquals(listOf(TimelineChange.KICKOFF), entries.map { it.change })
    }

    @Test
    fun everythingIsReplayedInTheOrderItWasLogged() {
        // The coach swaps b and c, then brings f on for c (now in b's old place), then changes formation.
        val entries = timeline(
            events = substitution(200, 15, out = "c", into = "f"),
            changes = listOf(
                change(0, 0, "1-2-1", kickoffSlots),
                change(100, 10, "1-2-1", kickoffSlots + mapOf(1 to "c", 2 to "b")),
                change(300, 20, "2-1-1", kickoffSlots + mapOf(1 to "f", 2 to "b"))
            )
        )
        assertEquals(
            listOf(TimelineChange.KICKOFF, TimelineChange.POSITIONS, TimelineChange.SUBSTITUTION, TimelineChange.FORMATION),
            entries.map { it.change }
        )
        assertEquals("f", entries[2].slots[1])
        assertEquals(listOf(2, 1, 1), entries[3].formation)
    }

    @Test
    fun aMatchKickedOffBeforeArrangementsWereKeptPlacesTheStartersByPosition() {
        val entries = timeline(
            events = substitution(100, 12, out = "a", into = "g"),
            changes = emptyList(),
            positions = mapOf("a" to PlayerPosition.STRIKER, "e" to PlayerPosition.GOALKEEPER)
        )
        assertEquals("e", entries.first().slots[0])
        assertEquals("a", entries.first().slots[4])
        assertEquals("g", entries.last().slots[4])
    }
}
