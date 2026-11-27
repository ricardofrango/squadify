package com.sportssoftw.squadcall.presentation.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import com.sportssoftw.squadcall.model.ClubTheme
import com.sportssoftw.squadcall.presentation.navigation.LocalAppNavigator
import com.sportssoftw.squadcall.presentation.navigation.LocalClubThemes
import com.sportssoftw.squadcall.presentation.navigation.topClubId

private val LightColorScheme = lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    secondary = md_theme_light_secondary,
    onSecondary = md_theme_light_onSecondary,
    background = md_theme_light_background,
    surface = md_theme_light_surface
)

private val DarkColorScheme = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    background = md_theme_dark_background,
    surface = md_theme_dark_surface
)

// Two themes (TECHNICAL_DECISIONS.md §58): SquadcallTheme, the app's own colours, wraps the whole app,
// so a screen that doesn't ask for a club's colours (splash, sign-in, My clubs) is in it; a club's
// screens wrap their content in SquadcallClubTheme.

/** Squadcall's own colours: carbon bars and green. */
@Composable
fun SquadcallTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme) {
        CompositionLocalProvider(LocalClubPalette provides SquadcallPalette, content = content)
    }
}

/**
 * A club's screen: the colours of the club it was opened under, the topmost club screen in the back
 * stack when it was first shown (kept with the screen, so it doesn't change when coming back to it).
 * Squadcall's colours when there's no club under it, or the club has none.
 */
@Composable
fun SquadcallClubTheme(content: @Composable () -> Unit) {
    val navigator = LocalAppNavigator.current
    val clubId = rememberSaveable { navigator?.topClubId().orEmpty() }
    SquadcallClubTheme(LocalClubThemes.current[clubId], content)
}

/** [theme]'s colours, or Squadcall's when it's null. */
@Composable
fun SquadcallClubTheme(theme: ClubTheme?, content: @Composable () -> Unit) {
    val palette = remember(theme) { theme?.toPalette() ?: SquadcallPalette }
    val colorScheme = MaterialTheme.colorScheme.copy(primary = palette.chrome)
    MaterialTheme(colorScheme = colorScheme) {
        CompositionLocalProvider(LocalClubPalette provides palette, content = content)
    }
}
