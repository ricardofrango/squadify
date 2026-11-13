package com.ricardofrango.squadify.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.ClubChrome
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.ScreenPadding
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme

// Every single-choice filter is this one segmented control (the developer, 2026-10-26): an outlined bar,
// the selected segment filled with the bar's colour. Sorting keeps the "Sort" label with chips
// (FilterChipRow's leadingLabel), as in the call-up.

/**
 * One choice of [options] as a segmented control, with one behaviour everywhere:
 *
 * - Every label is shown **whole**, on one line: a segment is as wide as its text needs, plus its padding.
 * - When the segments together are **narrower** than the room, the bar stretches to fill it. A segment
 *   that is already wider than an equal share keeps its own width; the rest share what is left equally.
 * - When they are **wider**, the bar is as wide as they are and scrolls sideways, off the screen if it
 *   has to. The outline scrolls with it, and [edgePadding] is part of the scrolled content on both
 *   sides, so the first and last segments can be scrolled clear of the screen's edge.
 *
 * [edgePadding] is the side padding of the content the bar sits in (the screen's, [ScreenPadding.Horizontal]):
 * the bar reaches out over it, to the edges of its parent, and keeps it as scrollable padding. Leave it
 * 0 for a bar that isn't at the edges: inside a card, or next to something in a row.
 */
@Composable
fun <T> SegmentedFilter(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    edgePadding: Dp = 0.dp
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier.fillMaxWidth().bleed(edgePadding).horizontalScroll(rememberScrollState()),
        // The room the bar has to fill (the parent's width) reaches the row as its minimum width.
        propagateMinConstraints = true
    ) {
        FillOrScrollRow(Modifier.padding(horizontal = edgePadding).clip(shape).border(1.5.dp, CarbonGray.copy(alpha = 0.16f), shape)) {
            options.forEach { option ->
                val text = label(option)
                Segment(text, option == selected) { onSelect(option) }
            }
        }
    }
}

@Composable
private fun Segment(text: String, isSelected: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(if (isSelected) ClubChrome else Color.White, tween(FilterAnimationMillis), label = "segmentBackground")
    val textColor by animateColorAsState(if (isSelected) Color.White else DeepCarbon, tween(FilterAnimationMillis), label = "segmentText")
    Box(
        Modifier
            .heightIn(min = 40.dp)
            .background(background)
            .semantics { role = Role.Tab; this.selected = isSelected; contentDescription = text }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
            color = textColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * Its children side by side, all as tall as the tallest, each as wide as its content needs; narrower than
 * the room there is (the minimum width it is given), they're stretched to fill it ([fillWidths]), and
 * wider, the row is as wide as they are.
 */
@Composable
private fun FillOrScrollRow(modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        if (measurables.isEmpty()) return@Layout layout(constraints.minWidth, constraints.minHeight) {}
        val height = measurables.maxOf { it.maxIntrinsicHeight(Int.MAX_VALUE) }.coerceAtLeast(constraints.minHeight)
        val natural = measurables.map { it.maxIntrinsicWidth(height) }
        val widths = fillWidths(natural, constraints.minWidth)
        val placeables = measurables.mapIndexed { i, m -> m.measure(Constraints.fixed(widths[i], height)) }
        layout(placeables.sumOf { it.width }, height) {
            var x = 0
            placeables.forEach { it.placeRelative(x, 0); x += it.width }
        }
    }
}

/**
 * [natural] widths stretched to add up to [room] when they fall short of it, else unchanged. A segment
 * at least as wide as an equal share of what is left keeps its width (and leaves the others a larger
 * share); the others get the same width each: "All teams" (wide) stays as it is, while "Team A" and
 * "Team B" grow equally.
 */
internal fun fillWidths(natural: List<Int>, room: Int): List<Int> {
    if (natural.isEmpty() || natural.sum() >= room) return natural
    val keeps = BooleanArray(natural.size)
    var left = room
    var sharing = natural.size
    var changed = true
    while (changed && sharing > 0) {
        changed = false
        val share = left / sharing
        for (i in natural.indices) {
            if (!keeps[i] && natural[i] >= share) {
                keeps[i] = true
                left -= natural[i]
                sharing--
                changed = true
            }
        }
    }
    val share = if (sharing > 0) left / sharing else 0
    // Whole pixels only: the last stretched segment takes the remainder, so the bar adds up to the room.
    val lastStretched = natural.indices.lastOrNull { !keeps[it] }
    return natural.mapIndexed { i, width ->
        when {
            keeps[i] -> width
            i == lastStretched -> share + left - share * sharing
            else -> share
        }
    }
}

private const val FilterAnimationMillis = 250

// ---- Previews ----

@Composable
private fun SegmentedFilterPreviewFrame() {
    SquadifyTheme {
        Column(Modifier.fillMaxWidth().background(Color(0xFFF4F5F7)).padding(horizontal = ScreenPadding.Horizontal, vertical = 16.dp)) {
            SegmentedFilter(listOf("All", "Goals", "Substitutions", "Cards"), "Goals", { it }, {}, edgePadding = ScreenPadding.Horizontal)
            Box(Modifier.height(12.dp))
            SegmentedFilter(listOf("Todas as equipas", "Infantis B1", "Infantis B2"), "Todas as equipas", { it }, {}, edgePadding = ScreenPadding.Horizontal)
            Box(Modifier.height(12.dp))
            SegmentedFilter(listOf("All teams", "Sub-15 A", "Sub-15 B", "Sub-15 C", "Sub-15 D", "Sub-15 E"), "All teams", { it }, {}, edgePadding = ScreenPadding.Horizontal)
        }
    }
}

@Preview
@Composable
private fun SegmentedFilterPreview() = SegmentedFilterPreviewFrame()

@Preview(widthDp = 900, heightDp = 480)
@Composable
private fun SegmentedFilterLandscapePreview() = SegmentedFilterPreviewFrame()
