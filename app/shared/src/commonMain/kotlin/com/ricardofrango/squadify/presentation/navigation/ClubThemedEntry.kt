package com.ricardofrango.squadify.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.toRoute
import com.ricardofrango.squadify.model.ClubTheme
import com.ricardofrango.squadify.presentation.ui.theme.ProvideClubPalette

/** The app's NavController, so each screen can tell which club it belongs to. */
val LocalAppNavController = staticCompositionLocalOf<NavController?> { null }

/** Every loaded club's colours, by club id (ClubThemeStore). */
val LocalClubThemes = compositionLocalOf<Map<String, ClubTheme?>> { emptyMap() }

/**
 * Draws a destination in its club's colours (TECHNICAL_DECISIONS.md §57). Its club is the topmost club
 * screen in the back stack when it was first shown, saved with the entry: everything opened from a
 * club wears that club's colours, while screens below every club (My clubs, sign-in) keep Squadify's,
 * even when coming back to them. Both screens of a transition keep their own colours as they slide.
 */
@Composable
fun ClubThemedEntry(entry: NavBackStackEntry, content: @Composable () -> Unit) {
    val navController = LocalAppNavController.current
    val clubId = rememberSaveable(entry.id) { navController?.topClubId().orEmpty() }
    ProvideClubPalette(LocalClubThemes.current[clubId], content)
}

private fun NavController.topClubId(): String? = try {
    getBackStackEntry<Screen.ClubDetails>().toRoute<Screen.ClubDetails>().clubId
} catch (e: IllegalArgumentException) {
    null // no club screen in the back stack
}
