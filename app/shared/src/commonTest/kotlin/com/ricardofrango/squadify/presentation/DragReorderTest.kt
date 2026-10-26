package com.ricardofrango.squadify.presentation

import com.ricardofrango.squadify.presentation.ui.training.dragTarget
import kotlin.test.Test
import kotlin.test.assertEquals

// A session's exercises are reordered by dragging: a row takes a neighbour's place once past its middle.
class DragReorderTest {
    private val heights = listOf(100, 100, 60, 100)

    @Test
    fun aShortDragKeepsThePlace() {
        assertEquals(1, dragTarget(1, 25f, heights))
        assertEquals(1, dragTarget(1, -40f, heights))
    }

    @Test
    fun pastTheMiddleOfTheNextRowItTakesItsPlace() {
        assertEquals(2, dragTarget(1, 31f, heights))
        assertEquals(3, dragTarget(1, 111f, heights))
    }

    @Test
    fun upwardsLikewise() {
        assertEquals(0, dragTarget(1, -51f, heights))
        assertEquals(2, dragTarget(3, -81f, heights))
        assertEquals(1, dragTarget(3, -111f, heights))
    }

    @Test
    fun itStopsAtTheEnds() {
        assertEquals(3, dragTarget(1, 5000f, heights))
        assertEquals(0, dragTarget(2, -5000f, heights))
    }
}
