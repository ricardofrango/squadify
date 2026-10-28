package com.ricardofrango.squadify.presentation.navigation

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.ricardofrango.squadify.model.ClubTheme

/** The app's navigator, so a club screen can tell which club it belongs to (SquadifyClubTheme). */
val LocalAppNavigator = staticCompositionLocalOf<AppNavigator?> { null }

/** Every loaded club's colours, by club id (ClubThemeStore). */
val LocalClubThemes = compositionLocalOf<Map<String, ClubTheme?>> { emptyMap() }

/** The club of the topmost club screen in the back stack, or null below every club. */
fun AppNavigator.topClubId(): String? =
    (backStack.lastOrNull { it.screen is Screen.ClubDetails }?.screen as? Screen.ClubDetails)?.clubId
