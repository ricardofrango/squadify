package com.ricardofrango.squadify.presentation

import com.ricardofrango.squadify.presentation.ui.components.shortFilterLabel
import kotlin.test.Test
import kotlin.test.assertEquals

// The stats filter's unselected segments show their first 3 letters.
class StatsFilterLabelTest {
    @Test
    fun unselectedLabelsAreTheirFirstThreeLetters() {
        assertEquals("Off", shortFilterLabel("Official"))
        assertEquals("Tou", shortFilterLabel("Tournament"))
        assertEquals("Tor", shortFilterLabel("Torneios"))
        assertEquals("All", shortFilterLabel("All"))
    }
}
