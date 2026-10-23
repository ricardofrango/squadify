package com.ricardofrango.squadify.presentation.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The space between a screen's edges and its content, the same on every screen (Age Group Details'
 * padding, the developer's pick, 2026-10-23). Cards and rows keep their own inner padding.
 */
object ScreenPadding {
    /** Start and end: also the side padding of a screen's pinned bottom bar, so buttons line up. */
    val Horizontal: Dp = 16.dp
    val Top: Dp = 14.dp
    val Bottom: Dp = 20.dp

    /** All four, for a `LazyColumn`'s `contentPadding`. */
    val Values = PaddingValues(start = Horizontal, end = Horizontal, top = Top, bottom = Bottom)
}

/** [ScreenPadding] around a screen's content (a scrolling column, or a centred loading/error state). */
fun Modifier.screenContentPadding(): Modifier = padding(ScreenPadding.Values)
