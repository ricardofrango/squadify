package com.ricardofrango.squadify.presentation.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.presentation.ui.theme.ClubChrome
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.IsClubThemed
import com.ricardofrango.squadify.presentation.ui.theme.ScreenPadding

/**
 * One sort chip: white, outlined, filled with the bar's colour when selected (sorting only, as the
 * call-up's "Sort: Position · Name"; filters are a SegmentedFilter since 2026-10-26). It takes exactly its 32dp height: without
 * that, Material pads its touch area to 48dp with invisible space above and below, which shows up as
 * extra padding around every row of chips.
 */
@Composable
fun SquadifyFilterChip(label: String, selected: Boolean, onClick: () -> Unit) = CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.White,
            selectedContainerColor = if (IsClubThemed) ClubChrome else DeepCarbon,
            selectedLabelColor = Color.White,
            labelColor = DeepCarbon
        )
    )
}

/**
 * A sort ("Sort" as [leadingLabel], then its options) as a row of [SquadifyFilterChip]s that scrolls
 * sideways edge to edge: the
 * screen's side padding is inside the row ([horizontalPadding]), so a chip scrolls to the screen's edge
 * instead of being cut at the padding. Inside content that already has that padding (a `LazyColumn`
 * item, a padded column) set [insidePaddedContent], and the row reaches out over it to the edges.
 */
@Composable
fun <T> FilterChipRow(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = ScreenPadding.Horizontal,
    insidePaddedContent: Boolean = false,
    // A word before the chips ("Sort"), scrolling with them.
    leadingLabel: String? = null
) {
    LazyRow(
        modifier = modifier
            .then(if (insidePaddedContent) Modifier.bleed(horizontalPadding) else Modifier)
            .fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leadingLabel?.let { item { Text(it, style = MaterialTheme.typography.bodySmall, color = DeepCarbon.copy(alpha = 0.5f)) } }
        items(options) { option -> SquadifyFilterChip(label(option), option == selected) { onSelect(option) } }
    }
}

/** Widens the content by [amount] on each side, over its parent's padding. */
private fun Modifier.bleed(amount: Dp) = layout { measurable, constraints ->
    val extra = (amount * 2).roundToPx()
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = (constraints.minWidth + extra).coerceAtLeast(0),
            maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + extra else constraints.maxWidth
        )
    )
    layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
}
