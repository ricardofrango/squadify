package com.ricardofrango.squadify.model

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class FormationTest {

    @Test
    fun aFormationIsThreeNumbersThatMakeTheTeamWithTheGoalkeeper() {
        assertEquals(listOf(2, 3, 1), parseFormation("2-3-1", 7))
        assertEquals(listOf(4, 4, 2), parseFormation(" 4 - 4 - 2 ", 11))
        assertNull(parseFormation("2-3-2", 7), "7 outfield players and a goalkeeper make 8")
        assertNull(parseFormation("2-3", 6))
        assertNull(parseFormation("4-2-3-1", 11))
        assertNull(parseFormation("2-x-1", 7))
        assertNull(parseFormation("-2-3-1", 7))
    }

    @Test
    fun eachTeamSizeHasItsDefault() {
        assertEquals("1-2-1", defaultFormation(5).formationText())
        assertEquals("2-3-1", defaultFormation(7).formationText())
        assertEquals("2-4-2", defaultFormation(9).formationText())
        assertEquals("4-4-2", defaultFormation(11).formationText())
        // Any other size still adds up.
        listOf(3, 4, 6, 8, 10).forEach { size -> assertEquals(size - 1, defaultFormation(size).sum(), "size $size") }
        // A saved formation that no longer fits the team falls back to the default.
        assertEquals(listOf(2, 3, 1), matchFormation("4-4-2", 7))
        assertEquals(listOf(3, 2, 1), matchFormation("3-2-1", 7))
    }

    @Test
    fun slotsRunGoalkeeperThenDefenceMidfieldAttack() {
        val f = listOf(2, 3, 1)
        assertEquals(PitchSpot(0, 0, 1), pitchSpot(f, 0))
        assertEquals(PitchSpot(1, 1, 2), pitchSpot(f, 2))
        assertEquals(PitchSpot(2, 0, 3), pitchSpot(f, 3))
        assertEquals(PitchSpot(3, 0, 1), pitchSpot(f, 6))
        // 2-3-1 to 1-3-2 keeps the slot numbers: slot 2 moves into midfield, slot 5 up front.
        val g = listOf(1, 3, 2)
        assertEquals(2, pitchSpot(g, 2).zone)
        assertEquals(3, pitchSpot(g, 5).zone)
        assertEquals(1, pitchSpot(g, 1).zone)
    }

    @Test
    fun withNoArrangementPlayersGoWhereTheirPositionSays() {
        val slots = autoArrangeSlots(
            listOf(2, 3, 1),
            listOf(
                "striker" to PlayerPosition.STRIKER,
                "keeper" to PlayerPosition.GOALKEEPER,
                "d1" to PlayerPosition.DEFENDER,
                "d2" to PlayerPosition.DEFENDER,
                "d3" to PlayerPosition.DEFENDER,
                "winger" to PlayerPosition.WINGER,
                "none" to null
            )
        )
        assertEquals("keeper", slots[0])
        assertEquals(setOf("d1", "d2"), setOf(slots[1], slots[2]))
        assertEquals("winger", slots[3])
        assertEquals("striker", slots[6])
        // The third defender and the player without a position take the free midfield slots.
        assertEquals(setOf("d3", "none"), setOf(slots[4], slots[5]))
    }

    private fun event(type: EventType, playerId: String, minute: Int, at: Long) =
        MatchEvent("e$at", "m", type, minute, playerId = playerId, createdAt = Instant.fromEpochMilliseconds(at))

    @Test
    fun theSubComingOnTakesTheSlotOfThePlayerGoingOff() {
        val stored = mapOf(0 to "gk", 1 to "a", 2 to "b")
        val events = listOf(
            event(EventType.SUBSTITUTION_OUT, "a", 10, 1),
            event(EventType.SUBSTITUTION_IN, "c", 10, 2),
            // c comes off again later for d: d ends up where a started.
            event(EventType.SUBSTITUTION_OUT, "c", 20, 3),
            event(EventType.SUBSTITUTION_IN, "d", 20, 4)
        )
        assertEquals(mapOf(0 to "gk", 1 to "d", 2 to "b"), currentPitchSlots(3, stored, setOf("gk", "d", "b"), events))
    }

    @Test
    fun anArrangementSavedAfterASubstitutionStands() {
        // a -> c at 10', then the coach swapped c and b: the saved arrangement already has c.
        val stored = mapOf(0 to "gk", 1 to "b", 2 to "c")
        val events = listOf(event(EventType.SUBSTITUTION_OUT, "a", 10, 1), event(EventType.SUBSTITUTION_IN, "c", 10, 2))
        assertEquals(stored, currentPitchSlots(3, stored, setOf("gk", "b", "c"), events))
    }

    @Test
    fun aSwapAfterRollingSubstitutionsStands() {
        // a -> c, c -> d, b -> a on one side; e -> g, g -> h, f -> e on the other; then the coach swapped
        // a and e. The saved arrangement already has every substitution in it.
        val events = listOf(
            event(EventType.SUBSTITUTION_OUT, "a", 10, 1), event(EventType.SUBSTITUTION_IN, "c", 10, 2),
            event(EventType.SUBSTITUTION_OUT, "c", 12, 3), event(EventType.SUBSTITUTION_IN, "d", 12, 4),
            event(EventType.SUBSTITUTION_OUT, "b", 14, 5), event(EventType.SUBSTITUTION_IN, "a", 14, 6),
            event(EventType.SUBSTITUTION_OUT, "e", 16, 7), event(EventType.SUBSTITUTION_IN, "g", 16, 8),
            event(EventType.SUBSTITUTION_OUT, "g", 18, 9), event(EventType.SUBSTITUTION_IN, "h", 18, 10),
            event(EventType.SUBSTITUTION_OUT, "f", 20, 11), event(EventType.SUBSTITUTION_IN, "e", 20, 12)
        )
        val onPitch = setOf("gk", "d", "a", "h", "e")
        val kickoff = mapOf(0 to "gk", 1 to "a", 2 to "b", 3 to "e", 4 to "f")
        val beforeSwap = currentPitchSlots(5, kickoff, onPitch, events)
        assertEquals(mapOf(0 to "gk", 1 to "d", 2 to "a", 3 to "h", 4 to "e"), beforeSwap)

        val swapped = mapOf(0 to "gk", 1 to "d", 2 to "e", 3 to "h", 4 to "a")
        val savedAt = Instant.fromEpochMilliseconds(13)
        assertEquals(swapped, currentPitchSlots(5, swapped, onPitch, events, savedAt))

        // A substitution after the swap still applies: b comes back on for a.
        val later = events + listOf(event(EventType.SUBSTITUTION_OUT, "a", 25, 14), event(EventType.SUBSTITUTION_IN, "b", 25, 15))
        assertEquals(swapped + (4 to "b"), currentPitchSlots(5, swapped, onPitch - "a" + "b", later, savedAt))
    }

    @Test
    fun aRedCardLeavesTheSlotEmptyAndAnUnplacedPlayerTakesAFreeOne() {
        val stored = mapOf(0 to "gk", 1 to "a")
        val events = listOf(event(EventType.RED_CARD, "a", 15, 1))
        // a is off (sent off); b is on the pitch with no slot, so takes the first free one.
        assertEquals(mapOf(0 to "gk", 1 to "b"), currentPitchSlots(3, stored, setOf("gk", "b"), events))
        assertEquals(mapOf(0 to "gk"), currentPitchSlots(3, stored, setOf("gk"), events))
    }
}
