package com.ricardofrango.squadify.presentation

import androidx.compose.ui.graphics.Color
import com.ricardofrango.squadify.model.ClubTheme
import com.ricardofrango.squadify.model.ClubThemeProblem
import com.ricardofrango.squadify.model.ClubThemeRules
import com.ricardofrango.squadify.presentation.ui.editclub.ClubFormFields
import com.ricardofrango.squadify.presentation.ui.editclub.hslToRgb
import com.ricardofrango.squadify.presentation.ui.editclub.rgbToHsl
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyPalette
import com.ricardofrango.squadify.presentation.ui.theme.hexColor
import com.ricardofrango.squadify.presentation.ui.theme.liftedForDark
import com.ricardofrango.squadify.presentation.ui.theme.toPalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Club colours: how a theme becomes the app's palette, and how Club settings sends it.
class ClubThemeTest {

    private val fields = ClubFormFields("Ribamar", "CDR", "", "", "", "")

    @Test
    fun theMainColourIsTheBarsAndTheAccentAndTheSecondIsWhatsSelectedOnTheBars() {
        val palette = ClubTheme("#E30613", "#FFFFFF").toPalette()
        assertEquals(Color(0xFFE30613), palette.chrome)
        assertEquals(Color(0xFFE30613), palette.accent)
        assertEquals(Color.White, palette.accentOnChrome)
        // Text on the selected chip is the main colour, not white on white.
        assertEquals(Color(0xFFE30613), palette.onAccentOnChrome)
    }

    @Test
    fun aBrokenThemeFallsBackToSquadifysColours() {
        assertEquals(SquadifyPalette, ClubTheme("red", "#FFFFFF").toPalette())
    }

    @Test
    fun darkMainColoursAreLiftedForTheDarkScreensButMidOnesAreLeftAlone() {
        val navy = 0x003C8F
        val lifted = liftedForDark(navy)
        assertTrue(ClubThemeRules.luminance(lifted) >= 0.13)
        // Still takes white text.
        assertTrue(ClubThemeRules.contrast(lifted, 0xFFFFFF) >= ClubThemeRules.MIN_CONTRAST)
        assertTrue(ClubThemeRules.luminance(liftedForDark(0x000000)) >= 0.13)
        // Red and Squadify green already read on the dark screens.
        assertEquals(0xE30613, liftedForDark(0xE30613))
        assertEquals(0x2E7D32, liftedForDark(0x2E7D32))
    }

    @Test
    fun savingSendsTheColoursOrClearsThem() {
        val withTheme = fields.copy(theme = ClubTheme("#e30613", "#ffffff")).toRequest()
        assertEquals(ClubTheme("#E30613", "#FFFFFF"), withTheme.theme)
        assertFalse(withTheme.clearTheme)

        val without = fields.toRequest()
        assertNull(without.theme)
        assertTrue(without.clearTheme)
    }

    @Test
    fun aPairThatBreaksTheRulesIsReported() {
        assertEquals(ClubThemeProblem.PRIMARY_TOO_LIGHT, fields.copy(theme = ClubTheme("#FDE100", "#000000")).themeProblem)
        assertNull(fields.copy(theme = ClubTheme("#000000", "#FDE100")).themeProblem)
        assertNull(fields.themeProblem)
    }

    @Test
    fun hslRoundTripsThroughThePicker() {
        listOf(0xE30613, 0x0B2B5B, 0xFDE100, 0x2E7D32, 0xFFFFFF, 0x000000).forEach { rgb ->
            val (h, s, l) = rgbToHsl(rgb).toList()
            val back = hslToRgb(h, s, l)
            // Within one step per channel (float rounding).
            listOf(16, 8, 0).forEach { shift ->
                val a = (rgb shr shift) and 0xFF
                val b = (back shr shift) and 0xFF
                assertTrue(kotlin.math.abs(a - b) <= 1, "${rgb.toString(16)} -> ${back.toString(16)}")
            }
        }
        assertEquals(Color(0xFF2E7D32), hexColor("#2E7D32"))
    }
}
