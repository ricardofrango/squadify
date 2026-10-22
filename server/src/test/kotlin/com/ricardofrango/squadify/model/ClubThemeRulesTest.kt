package com.ricardofrango.squadify.model

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// The colour rules the app checks as the user picks, and the server checks on save.
class ClubThemeRulesTest {

    private fun problem(primary: String, secondary: String) = ClubThemeRules.problem(ClubTheme(primary, secondary))

    @Test
    fun realClubColoursPass() {
        assertNull(problem("#E30613", "#FFFFFF")) // red and white
        assertNull(problem("#003C8F", "#FFFFFF")) // blue and white
        assertNull(problem("#000000", "#FDE100")) // black and yellow
        assertNull(problem("#1C2C5B", "#6CABDD")) // navy and sky blue
        assertNull(problem("#C8102E", "#FFD100")) // red and yellow
    }

    @Test
    fun theMainColourMustTakeWhiteText() {
        assertEquals(ClubThemeProblem.PRIMARY_TOO_LIGHT, problem("#FFD700", "#FFFFFF"))
        // Orange right at the 3:1 line still passes; a lighter one doesn't.
        assertNull(problem("#F36C21", "#FFFFFF"))
        assertEquals(ClubThemeProblem.PRIMARY_TOO_LIGHT, problem("#FF8C3A", "#FFFFFF"))
    }

    @Test
    fun theSecondColourMustBeClearlyLighter() {
        assertEquals(ClubThemeProblem.SECONDARY_TOO_CLOSE, problem("#D6001C", "#009A44"))
        assertEquals(ClubThemeProblem.SECONDARY_TOO_CLOSE, problem("#E30613", "#E30613"))
        // Darker than the main colour, even with enough contrast.
        assertEquals(ClubThemeProblem.SECONDARY_TOO_CLOSE, problem("#E30613", "#000000"))
    }

    @Test
    fun onlyFullHexColoursAreAccepted() {
        assertEquals(ClubThemeProblem.INVALID_COLOR, problem("#FFF", "#FFFFFF"))
        assertEquals(ClubThemeProblem.INVALID_COLOR, problem("E30613", "#FFFFFF"))
        assertEquals(ClubThemeProblem.INVALID_COLOR, problem("#E30613", "white"))
    }

    @Test
    fun hexRoundTrips() {
        assertEquals(0xE30613, ClubThemeRules.parse("#e30613"))
        assertEquals("#00A0FF", ClubThemeRules.toHex(0x00A0FF))
        assertEquals("#2E7D32", ClubThemeRules.normalize(" #2e7d32 "))
    }
}
