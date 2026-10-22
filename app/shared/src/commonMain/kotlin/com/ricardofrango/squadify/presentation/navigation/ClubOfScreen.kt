package com.ricardofrango.squadify.presentation.navigation

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavController
import androidx.navigation.toRoute
import com.ricardofrango.squadify.model.ClubTheme

/** The app's NavController, so a club screen can tell which club it belongs to (SquadifyClubTheme). */
val LocalAppNavController = staticCompositionLocalOf<NavController?> { null }

/** Every loaded club's colours, by club id (ClubThemeStore). */
val LocalClubThemes = compositionLocalOf<Map<String, ClubTheme?>> { emptyMap() }

/** The club of the topmost club screen in the back stack, or null below every club. */
fun NavController.topClubId(): String? = try {
    getBackStackEntry<Screen.ClubDetails>().toRoute<Screen.ClubDetails>().clubId
} catch (e: IllegalArgumentException) {
    null // no club screen in the back stack
}
