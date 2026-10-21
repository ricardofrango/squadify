package com.ricardofrango.squadify.presentation.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// Platform Base Colors - Modern Sports Management Theme
val CarbonGray = Color(0xFF1C1C1E)
val DeepCarbon = Color(0xFF121212)
val SquadifyGreen = Color(0xFF2E7D32) // Slightly deeper, more professional green

// The accent: Squadify's green, or the club's main colour inside a club (ClubPalette.kt). For green
// that means something (a win, done, available) use SuccessGreen, which never changes.
val PitchGreen: Color
    @Composable @ReadOnlyComposable get() = LocalClubPalette.current.accent
val SuccessGreen = SquadifyGreen
val PitchGreenLight = Color(0xFF4CAF50)
val WhiteSmoke = Color(0xFFF5F5F5)

// Status accents (design 23-27): amber marks something pending, deep green the top rating band,
// alert red the lowest band and destructive actions. AmberText is amber's readable text colour on an
// amber tint (plain amber text measures under 4.5:1 on white).
val Amber = Color(0xFFF9A825)
val AmberText = Color(0xFF7A5200)
val DeepGreen = Color(0xFF1B5E20)
val AlertRed = Color(0xFFC62828)

// Light Mode Palette
val md_theme_light_primary = CarbonGray
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_secondary = SquadifyGreen
val md_theme_light_onSecondary = Color(0xFFFFFFFF)
val md_theme_light_background = WhiteSmoke
val md_theme_light_surface = Color(0xFFFFFFFF)

// Dark Mode Palette
val md_theme_dark_primary = Color(0xFFE1E1E1)
val md_theme_dark_onPrimary = DeepCarbon
val md_theme_dark_secondary = PitchGreenLight
val md_theme_dark_onSecondary = Color(0xFF003300)
val md_theme_dark_background = DeepCarbon
val md_theme_dark_surface = Color(0xFF1C1C1E)
