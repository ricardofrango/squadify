package com.ricardofrango.squadify.presentation

import com.ricardofrango.squadify.presentation.ui.components.shortFilterLabel
import kotlin.test.Test
import kotlin.test.assertEquals

// The stats filter's unselected segments show their first 5 letters, with a "." when cut.
class StatsFilterLabelTest {
    @Test
    fun unselectedLabelsAreCutToFiveLettersWithADot() {
        assertEquals("Offic.", shortFilterLabel("Official"))
        assertEquals("Tourn.", shortFilterLabel("Tournament"))
        assertEquals("Amigá.", shortFilterLabel("Amigáveis"))
    }

    @Test
    fun shortLabelsStayWholeWithoutADot() {
        assertEquals("All", shortFilterLabel("All"))
        assertEquals("Todos", shortFilterLabel("Todos"))
    }
}
