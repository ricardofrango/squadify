package com.ricardofrango.squadify.presentation.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.presentation.ui.theme.SectionBorderColor

/** A section card's corners, the same everywhere. */
val SectionShape = RoundedCornerShape(16.dp)

/** A section card's grey outline. */
val SectionBorder = BorderStroke(1.dp, SectionBorderColor)

/**
 * A white section with a grey outline, on a screen's grey background ([com.ricardofrango.squadify.presentation.ui.theme.ScreenBackground]):
 * how every screen groups its content (the developer, 2026-10-25; TECHNICAL_DECISIONS.md §67).
 * [contentPadding] is the section's inner padding; a list of rows usually wants none and pads each row.
 * [spacing] is the gap between its children (a form's fields). With [onClick] the whole section is tappable.
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    spacing: Dp = 0.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val inner: @Composable () -> Unit = { Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(spacing), content = content) }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = SectionShape, color = Color.White, border = SectionBorder, content = inner)
    } else {
        Surface(modifier = modifier.fillMaxWidth(), shape = SectionShape, color = Color.White, border = SectionBorder, content = inner)
    }
}
