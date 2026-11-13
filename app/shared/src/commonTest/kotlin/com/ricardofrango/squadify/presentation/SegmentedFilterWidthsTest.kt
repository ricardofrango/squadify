package com.ricardofrango.squadify.presentation

import com.ricardofrango.squadify.presentation.ui.components.fillWidths
import kotlin.test.Test
import kotlin.test.assertEquals

class SegmentedFilterWidthsTest {
    @Test
    fun widerThanTheRoomStaysAsWideAsTheSegmentsAreSoItScrolls() {
        assertEquals(listOf(300, 200, 250), fillWidths(listOf(300, 200, 250), 600))
    }

    @Test
    fun theWidestKeepsItsWidthAndTheOthersGrowEqually() {
        // "Todas as equipas" is wider than an equal share (213), so it stays; the other two split the rest.
        assertEquals(listOf(264, 188, 188), fillWidths(listOf(264, 124, 140), 640))
    }

    @Test
    fun allNarrowShareTheRoomEqually() {
        assertEquals(listOf(100, 100, 100, 100), fillWidths(listOf(40, 60, 80, 70), 400))
    }

    @Test
    fun theLastStretchedSegmentTakesTheRemainderSoTheBarFillsTheRoomExactly() {
        val widths = fillWidths(listOf(10, 10, 10), 100)
        assertEquals(100, widths.sum())
        assertEquals(listOf(33, 33, 34), widths)
    }

    @Test
    fun aKeptWideSegmentCanPushAnotherPastItsShareToo() {
        // Equal share is 150: 200 keeps; then 100 left over two, share 50: 120 keeps; 80 ends up alone with 20.. no: it keeps too.
        assertEquals(listOf(200, 120, 80), fillWidths(listOf(200, 120, 80), 400).also { assertEquals(400, it.sum()) })
    }
}
