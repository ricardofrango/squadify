package com.ricardofrango.squadify.presentation.ui.components

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.window.core.layout.WindowSizeClass
import com.ricardofrango.squadify.presentation.ui.theme.ScreenPadding

// How a screen adapts to the space it gets (TECHNICAL_DECISIONS.md §11.5). The breakpoints are
// Material's window size classes (material3-adaptive / window-core): compact below 600dp wide, medium
// and up from there. Two kinds of decision:
// - Navigation (a rail or a bottom bar) follows the window's size class, [useNavigationRail].
// - A screen's layout measures the space it actually has (a BoxWithConstraints), not the window, so a
//   screen next to a rail or in a resized desktop window decides on what it really has.

/** Material's medium width: from here there's room for two panes side by side, or a rail. */
val MediumWidth: Dp = WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND.dp

/**
 * True from a medium-width window up (a phone on its side, a tablet, an unfolded foldable, desktop,
 * a browser): the tabs go in a rail on the left instead of a bottom bar. Material's navigation suite
 * would keep a bottom bar on a phone on its side (a compact height); the app's design (32e, 33e)
 * moves it to the rail there too, since a bar costs too much of a short screen.
 */
@Composable
fun useNavigationRail(): Boolean =
    currentWindowAdaptiveInfoV2().windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

/**
 * True while the keyboard is open in a window under 480dp tall (a phone lying down). The keyboard
 * leaves about the top bar's height there, so a form's pinned Save bar hides while typing, or it
 * would cover the field being typed in; it's back as the keyboard closes (TECHNICAL_DECISIONS.md §11.6).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun keyboardOnShortWindow(): Boolean =
    WindowInsets.isImeVisible &&
        !currentWindowAdaptiveInfoV2().windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)

/** The widest a form, or a list of single rows, gets; centred beyond that, so rows never stretch across a wide window. */
val ReadableMaxWidth: Dp = 720.dp

/** At least [MediumWidth] wide: room for two columns, whatever the orientation. */
val BoxWithConstraintsScope.isWide: Boolean get() = maxWidth >= MediumWidth

/**
 * Wider than tall and at least [MediumWidth] wide: a phone on its side, a tablet or a desktop
 * window. The screens that save height here (side-by-side panes, a shorter header) use this.
 */
val BoxWithConstraintsScope.isLandscapeLayout: Boolean get() = maxWidth > maxHeight && maxWidth >= MediumWidth

/**
 * Fills the width it's given, up to [max], centred beyond that. For a scrolling column, put it after
 * `verticalScroll` so the whole width still scrolls; a `LazyColumn` uses [readableListPadding] instead.
 */
fun Modifier.readableWidth(max: Dp = ReadableMaxWidth): Modifier = layout { measurable, constraints ->
    // Unbounded (inside a horizontal scroll): nothing to fill or centre.
    if (!constraints.hasBoundedWidth) {
        val placeable = measurable.measure(constraints)
        return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    val width = constraints.maxWidth
    val contentWidth = minOf(width, max.roundToPx())
    val placeable = measurable.measure(constraints.copy(minWidth = contentWidth, maxWidth = contentWidth))
    layout(width, placeable.height) { placeable.place((width - placeable.width) / 2, 0) }
}

/**
 * The side padding that lines a list's rows up with a [readableWidth] block holding the usual
 * [ScreenPadding.Horizontal]: that padding, plus whatever is left either side of [max].
 */
fun readableSidePadding(width: Dp, max: Dp = ReadableMaxWidth): Dp = ScreenPadding.Horizontal + max(0.dp, (width - max) / 2)

/** [ScreenPadding.Values] for a list in this box, with the sides widened by [readableSidePadding]. */
fun BoxWithConstraintsScope.readableListPadding(
    top: Dp = ScreenPadding.Top,
    bottom: Dp = ScreenPadding.Bottom,
    max: Dp = ReadableMaxWidth
): PaddingValues {
    val side = readableSidePadding(maxWidth, max)
    return PaddingValues(start = side, end = side, top = top, bottom = bottom)
}

/**
 * A screen's `Scaffold(contentWindowInsets = ScreenWindowInsets)`: the content keeps clear of the system
 * bars and, on a phone lying down, of the camera cutout at the side, while the screen's colour still
 * runs to the edges (TECHNICAL_DECISIONS.md §11.6). Material's default leaves the cutout out, which put
 * text under the camera.
 */
val ScreenWindowInsets: WindowInsets
    @Composable get() = WindowInsets.safeDrawing

/** For a screen without a Scaffold: `statusBarsPadding()`, plus the camera cutout at the side lying down. */
@Composable
fun Modifier.safeTopPadding(): Modifier =
    windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))

/** For a bar pinned to the bottom: `navigationBarsPadding()`, plus the camera cutout at the side lying down. */
@Composable
fun Modifier.safeBottomPadding(): Modifier =
    windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))

/**
 * For a row added under the top bar (a status band, a strip): only the sides, so it lines up with the
 * bar's title and keeps clear of the camera cutout lying down.
 */
@Composable
fun Modifier.safeSidesPadding(): Modifier =
    windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
