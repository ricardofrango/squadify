package com.ricardofrango.squadify.presentation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreenLight
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme

/**
 * The app's brand mark: a 3-3-4 formation of dots (the app icon's "18a" design, see
 * AI_GUIDELINES.md "UI Design Reference"), drawn as vector circles rather than an imported bitmap
 * so it stays crisp at any [size] and can be recolored per surface (dark header vs. light card).
 */
@Composable
fun SquadifyMark(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    lightDotColor: Color = PitchGreenLight,
    darkDotColor: Color = PitchGreen
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val dotRadius = w * 0.054f

        fun dot(xFrac: Float, yFrac: Float, color: Color) {
            drawCircle(color = color, radius = dotRadius, center = Offset(w * xFrac, h * yFrac))
        }

        // Top row -- lighter green
        dot(0.194f, 0.281f, lightDotColor)
        dot(0.499f, 0.281f, lightDotColor)
        dot(0.804f, 0.281f, lightDotColor)
        // Middle row -- darker green, inset from the top row
        dot(0.320f, 0.499f, darkDotColor)
        dot(0.499f, 0.499f, darkDotColor)
        dot(0.678f, 0.499f, darkDotColor)
        // Bottom row -- darker green, same span as the top row
        dot(0.194f, 0.697f, darkDotColor)
        dot(0.393f, 0.697f, darkDotColor)
        dot(0.591f, 0.697f, darkDotColor)
        dot(0.788f, 0.697f, darkDotColor)
    }
}

@Preview
@Composable
private fun SquadifyMarkPreview() {
    SquadifyTheme {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.background(DeepCarbon).size(160.dp),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            SquadifyMark(size = 120.dp)
        }
    }
}
