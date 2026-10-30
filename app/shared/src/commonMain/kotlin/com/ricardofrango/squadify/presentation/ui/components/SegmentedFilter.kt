package com.ricardofrango.squadify.presentation.ui.components

import com.ricardofrango.squadify.presentation.ui.theme.ScreenPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme

// Every single-choice filter is one segmented control (the developer, 2026-10-26), the look the match
// type filter had: an outlined bar, the selected segment filled with the bar's colour. Sorting keeps
// the "Sort" label with chips (FilterChipRow's leadingLabel), as in the call-up.

/**
 * One choice of [options] as a segmented control.
 *
 * - **A fixed set** (types, statuses, roles): the unselected segments show [shortLabel] ("Offic.",
 *   "Tourn.") and only the selected one its full name, so four or five fit side by side. Switching
 *   animates: the segments' widths, the label and the background move together.
 * - **Names** ([shorten] false, e.g. teams, which a cut would make alike: "Sub-1."): every segment
 *   shows its whole label; they fill the width when they fit and scroll sideways inside the outline
 *   when they don't.
 * - **Past the screen's edge** ([shorten] false and [insidePaddedContent], inside padded content):
 *   the whole bar, outline included, runs past the screen's padding to its edges and scrolls sideways,
 *   so the last options are scrolled to rather than cut inside the outline.
 */
@Composable
fun <T> SegmentedFilter(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    shorten: Boolean = true,
    insidePaddedContent: Boolean = false,
    shortLabel: (T, String) -> String = { _, full -> shortFilterLabel(full) }
) {
    val shape = RoundedCornerShape(12.dp)
    val outlined = modifier.fillMaxWidth().clip(shape).border(1.5.dp, CarbonGray.copy(alpha = 0.16f), shape)
    if (shorten) {
        Row(outlined.height(IntrinsicSize.Min)) {
            options.forEach { option ->
                val isSelected = option == selected
                val full = label(option)
                val short = shortLabel(option, full)
                // The selected segment takes the room its full name needs; the others, their short label.
                val weight by animateFloatAsState(
                    (if (isSelected) full.length else short.length).coerceAtLeast(3).toFloat() + 2f,
                    animationSpec = tween(FilterAnimationMillis), label = "segmentWeight"
                )
                Segment(full, if (isSelected) full else short, isSelected, Modifier.weight(weight).fillMaxHeight(), sidePadding = 4.dp) { onSelect(option) }
            }
        }
    } else if (insidePaddedContent) {
        BoxWithConstraints(modifier.fillMaxWidth().bleed(ScreenPadding.Horizontal)) {
            // The screen's width minus its padding: the bar fills it when its options fit.
            val room = maxWidth - ScreenPadding.Horizontal * 2
            Box(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = ScreenPadding.Horizontal)) {
                FillOrScrollRow(Modifier.widthIn(min = room).clip(shape).border(1.5.dp, CarbonGray.copy(alpha = 0.16f), shape)) {
                    options.forEach { option ->
                        val full = label(option)
                        Segment(full, full, option == selected, Modifier, sidePadding = 14.dp) { onSelect(option) }
                    }
                }
            }
        }
    } else {
        // The row itself carries the outline: a Box between them would drop the outline's width from
        // the row's constraints, leaving the segments short of the right edge with a blank gap there.
        FillOrScrollRow(outlined.horizontalScroll(rememberScrollState())) {
            options.forEach { option ->
                val full = label(option)
                Segment(full, full, option == selected, Modifier, sidePadding = 14.dp) { onSelect(option) }
            }
        }
    }
}

@Composable
private fun Segment(full: String, shown: String, isSelected: Boolean, modifier: Modifier, sidePadding: Dp, onClick: () -> Unit) {
    val background by animateColorAsState(if (isSelected) ClubChrome else Color.White, tween(FilterAnimationMillis), label = "segmentBackground")
    val textColor by animateColorAsState(if (isSelected) Color.White else DeepCarbon, tween(FilterAnimationMillis), label = "segmentText")
    Box(
        modifier
            .heightIn(min = 40.dp)
            .background(background)
            // The full name for screen readers, whatever is shown.
            .semantics { role = Role.Tab; this.selected = isSelected; contentDescription = full }
            .clickable(onClick = onClick)
            .padding(horizontal = sidePadding, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = shown,
            transitionSpec = { fadeIn(tween(FilterAnimationMillis)) togetherWith fadeOut(tween(FilterAnimationMillis / 2)) },
            label = "segmentLabel"
        ) { text ->
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
}

/**
 * Its children side by side at their own widths, all as tall as the tallest. When they're narrower than
 * the room there is ([minWidth], the outline's width), the rest is shared out so they fill it; when
 * wider, the row is as wide as they are and the outline scrolls.
 */
@Composable
private fun FillOrScrollRow(modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val height = measurables.maxOf { it.maxIntrinsicHeight(Int.MAX_VALUE) }.coerceAtLeast(constraints.minHeight)
        val widths = measurables.map { it.maxIntrinsicWidth(height) }
        // The outline's own width, which horizontalScroll leaves out of the constraints.
        val room = constraints.minWidth
        val extra = if (widths.sum() < room && widths.isNotEmpty()) (room - widths.sum()) / widths.size else 0
        val placeables = measurables.mapIndexed { i, m ->
            val w = widths[i] + extra + if (i == widths.lastIndex && extra > 0) (room - widths.sum()) % widths.size else 0
            m.measure(Constraints.fixed(w, height))
        }
        layout(maxOf(placeables.sumOf { it.width }, room), height) {
            var x = 0
            placeables.forEach { it.placeRelative(x, 0); x += it.width }
        }
    }
}

private const val FilterAnimationMillis = 250

/** "Official" → "Offic.", the "." saying it's cut; a label of 5 letters or fewer stays whole ("All", "Todos"). */
fun shortFilterLabel(label: String): String = if (label.length <= ShortLabelLength) label else label.take(ShortLabelLength) + "."

private const val ShortLabelLength = 5

// ---- Previews ----

@Composable
private fun SegmentedFilterPreviewFrame() {
    SquadifyTheme {
        Column(Modifier.fillMaxWidth().background(Color(0xFFF4F5F7)).padding(16.dp)) {
            SegmentedFilter(listOf("All", "Goals", "Substitutions", "Cards"), "Goals", { it }, {})
            Box(Modifier.height(12.dp))
            SegmentedFilter(listOf("All teams", "Sub-15 A", "Sub-15 B"), "Sub-15 A", { it }, {}, shorten = false)
            Box(Modifier.height(12.dp))
            SegmentedFilter(listOf("All teams", "Sub-15 A", "Sub-15 B", "Sub-15 C", "Sub-15 D", "Sub-15 E"), "All teams", { it }, {}, shorten = false)
        }
    }
}

@Preview
@Composable
private fun SegmentedFilterPreview() = SegmentedFilterPreviewFrame()

@Preview(widthDp = 900, heightDp = 480)
@Composable
private fun SegmentedFilterLandscapePreview() = SegmentedFilterPreviewFrame()
