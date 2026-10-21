package com.ricardofrango.squadify.presentation.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.ricardofrango.squadify.model.ClubTheme
import com.ricardofrango.squadify.model.ClubThemeRules

/**
 * The colours a club's screens are drawn in (TECHNICAL_DECISIONS.md §57).
 * - [chrome]: the dark bars (top bar, bottom tabs, detail headers, More panel). White text sits on it.
 * - [accent]: buttons, selected states and links on the white screens ([PitchGreen] reads it).
 * - [accentOnChrome]: a selected tab or chip on [chrome], with [onAccentOnChrome] for text on it.
 * - [accentOnDark]: [accent] lifted just enough to read on the dark screens (the live match console),
 *   still dark enough for white text. Squadify's green and most club colours need no lifting; navy or
 *   black become a mid blue or grey.
 * Without a club theme it's Squadify's own: carbon bars with green.
 */
@Immutable
data class ClubPalette(
    val chrome: Color,
    val accent: Color,
    val accentOnChrome: Color,
    val onAccentOnChrome: Color,
    val accentOnDark: Color
)

val SquadifyPalette = ClubPalette(
    chrome = CarbonGray,
    accent = SquadifyGreen,
    accentOnChrome = SquadifyGreen,
    onAccentOnChrome = Color.White,
    accentOnDark = SquadifyGreen
)

val LocalClubPalette = staticCompositionLocalOf { SquadifyPalette }

/** The club's main colour becomes both the bars and the accent; its light colour marks what's selected on the bars. */
fun ClubTheme.toPalette(): ClubPalette {
    val primary = ClubThemeRules.parse(primaryColor)?.let(::rgbColor) ?: return SquadifyPalette
    val secondary = ClubThemeRules.parse(secondaryColor)?.let(::rgbColor) ?: return SquadifyPalette
    return ClubPalette(
        chrome = primary,
        accent = primary,
        accentOnChrome = secondary,
        onAccentOnChrome = primary,
        accentOnDark = rgbColor(liftedForDark(ClubThemeRules.parse(primaryColor)!!))
    )
}

// The dark screens are DeepCarbon; 0.13 luminance reads on it at 3:1, and stays under the 0.30 that
// white text needs (ClubThemeRules.MIN_CONTRAST). Mixes towards white in small steps until it's there.
private const val MIN_LUMINANCE_ON_DARK = 0.13

internal fun liftedForDark(rgb: Int): Int {
    var mixed = rgb
    var step = 0
    while (ClubThemeRules.luminance(mixed) < MIN_LUMINANCE_ON_DARK && step < 20) {
        step++
        val t = step / 20.0
        fun mix(shift: Int): Int {
            val c = (rgb shr shift) and 0xFF
            return (c + (255 - c) * t).toInt().coerceIn(0, 255)
        }
        mixed = (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }
    return mixed
}

fun rgbColor(rgb: Int) = Color(0xFF000000 or rgb.toLong())

/** "#E30613" as a Color, or null when it isn't one. */
fun hexColor(hex: String): Color? = ClubThemeRules.parse(hex)?.let(::rgbColor)

/** Draws [content] in [theme]'s colours, or Squadify's when it's null. */
@Composable
fun ProvideClubPalette(theme: ClubTheme?, content: @Composable () -> Unit) {
    val palette = remember(theme) { theme?.toPalette() ?: SquadifyPalette }
    CompositionLocalProvider(LocalClubPalette provides palette, content = content)
}

/** The bars' colour: [CarbonGray], or the club's main colour. */
val ClubChrome: Color
    @Composable @ReadOnlyComposable get() = LocalClubPalette.current.chrome

/** What's selected on [ClubChrome]: green, or the club's light colour. */
val ChromeAccent: Color
    @Composable @ReadOnlyComposable get() = LocalClubPalette.current.accentOnChrome

/** Whether a club's own colours are in use (not Squadify's). */
val IsClubThemed: Boolean
    @Composable @ReadOnlyComposable get() = LocalClubPalette.current != SquadifyPalette

/** Text on a [ChromeAccent] fill: white, or the club's main colour. */
val OnChromeAccent: Color
    @Composable @ReadOnlyComposable get() = LocalClubPalette.current.onAccentOnChrome

/** For a dark screen: [PitchGreen] there reads the lifted [ClubPalette.accentOnDark]. */
@Composable
fun OnDarkSurface(content: @Composable () -> Unit) {
    val palette = LocalClubPalette.current
    val dark = remember(palette) { palette.copy(accent = palette.accentOnDark) }
    CompositionLocalProvider(LocalClubPalette provides dark, content = content)
}
