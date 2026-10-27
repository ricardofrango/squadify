package com.ricardofrango.squadify.presentation.ui.components

import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.ricardofrango.squadify.presentation.ui.theme.ScreenPadding

// How a screen adapts to the space it gets (TECHNICAL_DECISIONS.md §11.5). Every check measures the
// space the screen actually has (a BoxWithConstraints), not the device, so a screen next to a rail or
// in a resized desktop window decides on what it really has. The breakpoint is Material's: compact
// below 600dp, medium and up from there.

/** Material's medium width: from here there's room for two panes side by side, or a rail. */
val MediumWidth: Dp = 600.dp

/** The widest a form, or a list of single rows, gets; centred beyond that, so rows never stretch across a wide window. */
val ReadableMaxWidth: Dp = 720.dp

/** At least [MediumWidth] wide: room for two columns, whatever the orientation. */
val BoxWithConstraintsScope.isWide: Boolean get() = maxWidth >= MediumWidth

/**
 * Wider than tall and at least [MediumWidth] wide: a phone on its side, a tablet or a desktop
 * window. The screens that save height here (a rail instead of a bottom bar, side-by-side panes) use this.
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
