package com.ricardofrango.squadify.presentation.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.match_format_value

/**
 * How many a side a match is played ("5x5 · 7x7 · 9x9 · 11x11"), for Schedule match, Edit match and
 * the result of a game not followed live. [options] are the club sport's team sizes; it starts on the
 * team's own, and another is that match only. [selected] null: none yet, the user must pick one (the coach
 * app's age-group wizard).
 */
@Composable
fun TeamSizeSelector(options: List<Int>, selected: Int?, onSelected: (Int) -> Unit, modifier: Modifier = Modifier, edgePadding: Dp = 0.dp) {
    SegmentedFilter<Int?>(
        options = options,
        selected = selected,
        label = { stringResource(Res.string.match_format_value, it ?: 0) },
        onSelect = { it?.let(onSelected) },
        modifier = modifier,
        edgePadding = edgePadding
    )
}
