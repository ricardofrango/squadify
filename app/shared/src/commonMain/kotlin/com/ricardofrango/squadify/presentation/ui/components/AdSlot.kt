package com.ricardofrango.squadify.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.window.core.layout.WindowSizeClass
import com.ricardofrango.squadify.presentation.ui.theme.ScreenBackground

/**
 * A banner ad for the space a screen shows (the coach app's Free plan, REQUIREMENTS §5c), set by the coach
 * app: it draws the ad, or nothing on a plan without ads. Null in the clubs app, which has none.
 */
val LocalAdBanner = staticCompositionLocalOf<(@Composable (spaceId: String?) -> Unit)?> { null }

/**
 * The ad slot at the bottom of a browsing screen (Home, an age group, a team, a player), for the space it
 * shows ([LocalShownSpace]). Never on a live game, a form, sign-in or Plans: those screens don't place it.
 * Not on a window under 480dp tall either (a phone lying down), which has no height to spare. [alone]:
 * nothing under it, so it keeps clear of the navigation bar itself; above a tab bar, the bar does.
 */
@Composable
fun AdBanner(spaceId: String? = LocalShownSpace.current, alone: Boolean = false) {
    val banner = LocalAdBanner.current ?: return
    if (!currentWindowAdaptiveInfoV2().windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)) return
    Box(Modifier.fillMaxWidth().background(ScreenBackground).then(if (alone) Modifier.safeBottomPadding() else Modifier)) {
        banner(spaceId)
    }
}
