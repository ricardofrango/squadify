package com.ricardofrango.squadify.presentation.ui.livematch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PitchLineupTest {

    private val slots = mapOf(0 to "gk", 1 to "a", 3 to "b")

    @Test
    fun twoPlayersSwapPlacesOrOneMovesIntoAnEmptyPosition() {
        assertEquals(mapOf(0 to "gk", 1 to "b", 3 to "a"), slots.swapped(1, 3))
        assertEquals(mapOf(0 to "gk", 2 to "a", 3 to "b"), slots.swapped(1, 2))
    }

    @Test
    fun placingAPlayerSendsWhoeverStoodThereToTheBench() {
        // c takes a's place; a is no longer on the pitch.
        assertEquals(mapOf(0 to "gk", 1 to "c", 3 to "b"), slots.placed(1, "c"))
        // b moves from 3 to 2, leaving 3 empty.
        assertEquals(mapOf(0 to "gk", 1 to "a", 2 to "b"), slots.placed(2, "b"))
    }

    @Test
    fun theFirstFreePositionGoesInSlotOrder() {
        assertEquals(2, slots.firstFreeSlot(7))
        assertNull(mapOf(0 to "gk", 1 to "a", 2 to "b").firstFreeSlot(3))
    }
}
