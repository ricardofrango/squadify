package com.ricardofrango.squadify.model

import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * A club's two colours, as "#RRGGBB". Both or neither: a club either has a theme or uses Squadify's own
 * colours. [primaryColor] is the strong one -- the bars and buttons, with white text on it;
 * [secondaryColor] is the light one, used for what sits on top of the primary (the selected tab, the
 * initials on a logo placeholder). See [ClubThemeRules] and TECHNICAL_DECISIONS.md §57.
 */
@Serializable
data class ClubTheme(
    val primaryColor: String,
    val secondaryColor: String
)

/** Why a theme was refused. The same rules run in the app (as the user picks) and on the server. */
enum class ClubThemeProblem {
    /** Not a "#RRGGBB" colour. */
    INVALID_COLOR,
    /** White text on the primary would be hard to read. */
    PRIMARY_TOO_LIGHT,
    /** The secondary isn't clearly lighter than the primary. */
    SECONDARY_TOO_CLOSE
}

object ClubThemeRules {
    /** WCAG's 3:1, the minimum for bold text and UI parts. */
    const val MIN_CONTRAST = 3.0

    private val HEX = Regex("^#[0-9A-Fa-f]{6}$")
    private const val WHITE = 0xFFFFFF

    fun isValidColor(hex: String): Boolean = HEX.matches(hex)

    /** "#2e7d32" -> "#2E7D32". */
    fun normalize(hex: String): String = hex.trim().uppercase()

    /** The colour as 0xRRGGBB, or null if it isn't "#RRGGBB". */
    fun parse(hex: String): Int? = hex.trim().takeIf(::isValidColor)?.substring(1)?.toInt(16)

    fun toHex(rgb: Int): String = "#" + (rgb and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()

    /** WCAG relative luminance, 0 (black) to 1 (white). */
    fun luminance(rgb: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((rgb shr shift) and 0xFF) / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** The first rule [theme] breaks, or null when it's fine. */
    fun problem(theme: ClubTheme): ClubThemeProblem? {
        val primary = parse(theme.primaryColor) ?: return ClubThemeProblem.INVALID_COLOR
        val secondary = parse(theme.secondaryColor) ?: return ClubThemeProblem.INVALID_COLOR
        if (contrast(primary, WHITE) < MIN_CONTRAST) return ClubThemeProblem.PRIMARY_TOO_LIGHT
        if (luminance(secondary) <= luminance(primary) || contrast(primary, secondary) < MIN_CONTRAST) {
            return ClubThemeProblem.SECONDARY_TOO_CLOSE
        }
        return null
    }

    fun normalized(theme: ClubTheme) = ClubTheme(normalize(theme.primaryColor), normalize(theme.secondaryColor))
}
