package com.ricardofrango.squadify.presentation

import com.ricardofrango.squadify.model.Opponent
import com.ricardofrango.squadify.presentation.ui.opponents.filterOpponents
import com.ricardofrango.squadify.presentation.ui.opponents.groupByLetter
import com.ricardofrango.squadify.presentation.ui.opponents.opponentInitials
import com.ricardofrango.squadify.presentation.ui.opponents.recentOpponents
import kotlin.test.Test
import kotlin.test.assertEquals

// The Opponents list (design 38i): search ignores case and accents, on name and short name.
class OpponentSearchTest {
    private val fontelas = Opponent("o1", "c", "AD Fontelas", "ADF")
    private val academica = Opponent("o2", "c", "Académica Foz do Vez", "AFV")
    private val malveira = Opponent("o3", "c", "CF Os Malveirenses", "CFM")
    private val sobreiro = Opponent("o4", "c", "GD Sobreirense")
    private val numbered = Opponent("o5", "c", "1º de Maio")
    private val all = listOf(fontelas, academica, malveira, sobreiro, numbered)

    @Test
    fun searchIgnoresCaseAndAccents() {
        assertEquals(listOf(academica), filterOpponents(all, "ACADEMICA"))
        assertEquals(listOf(academica), filterOpponents(all, "académ"))
    }

    @Test
    fun searchMatchesTheShortName() {
        assertEquals(listOf(malveira), filterOpponents(all, "cfm"))
    }

    @Test
    fun aBlankSearchKeepsEveryOpponent() {
        assertEquals(all, filterOpponents(all, "  "))
    }

    @Test
    fun groupsAreByFirstLetterWithAccentsFoldedAndOthersLast() {
        val groups = groupByLetter(all)
        assertEquals(listOf("A", "C", "G", "#"), groups.map { it.first })
        assertEquals(listOf(academica, fontelas), groups.first().second)
    }

    @Test
    fun recentKeepsTheServerOrderAndSkipsMissingOnes() {
        assertEquals(listOf(malveira, fontelas), recentOpponents(all, listOf("o3", "gone", "o1")))
    }

    @Test
    fun initialsAreTheShortNameElseTheWordsFirstLetters() {
        assertEquals("ADF", opponentInitials("AD Fontelas", "adf"))
        assertEquals("GS", opponentInitials("GD Sobreirense", null))
        assertEquals("COM", opponentInitials("CF Os Malveirenses Juniores", " "))
    }
}
