package com.ricardofrango.squadify.presentation.ui.livematch

import kotlin.test.Test
import kotlin.test.assertEquals

/** The formation sheet's field and its list of formations. */
class FormationInputTest {

    @Test
    fun aDigitGetsADashUntilThereAreThreeNumbers() {
        assertEquals("2-", withAutoDash("", "2"))
        assertEquals("2-3-", withAutoDash("2-", "2-3"))
        assertEquals("2-3-1", withAutoDash("2-3-", "2-3-1"))
    }

    @Test
    fun deletingTheDashIsLeftAlone() {
        assertEquals("2", withAutoDash("2-", "2"))
        assertEquals("2-3", withAutoDash("2-3-", "2-3"))
    }

    @Test
    fun aDashTypedAfterTheAutomaticOneIsNotDoubled() {
        assertEquals("2-", withAutoDash("2-", "2--"))
    }

    @Test
    fun theFormationsAreInOrder() {
        val formations = quickFormations(9)
        assertEquals(formations.sortedWith(compareBy({ it[0] }, { it[1] }, { it[2] })), formations)
        assertEquals(5, formations.size)
    }
}
