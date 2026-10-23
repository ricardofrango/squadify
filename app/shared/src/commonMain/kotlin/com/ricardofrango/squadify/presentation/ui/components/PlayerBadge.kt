package com.ricardofrango.squadify.presentation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme

/** What a player's badge shows, from what the player has. */
enum class PlayerBadgeContent {
    PHOTO_AND_NUMBER, PHOTO, NUMBER, PLACEHOLDER;

    companion object {
        fun of(photoUrl: String?, shirtNumber: Int?): PlayerBadgeContent = when {
            photoUrl != null && shirtNumber != null -> PHOTO_AND_NUMBER
            photoUrl != null -> PHOTO
            shirtNumber != null -> NUMBER
            else -> PLACEHOLDER
        }
    }
}

/**
 * The leading badge of a player's row, the same width in every row so the names line up:
 * - a photo and a shirt number: the photo, with the number under it;
 * - only a photo: the photo, centred;
 * - only a number: the number, centred;
 * - neither: a head-and-shoulders placeholder.
 * [numberColor] and [placeholderColor] suit the surface (the live console is dark).
 */
@Composable
fun PlayerBadge(
    photoUrl: String?,
    shirtNumber: Int?,
    modifier: Modifier = Modifier,
    photoSize: Dp = 34.dp,
    numberColor: Color = PitchGreen,
    placeholderColor: Color = DeepCarbon.copy(alpha = 0.3f),
    numberStyle: TextStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black)
) {
    Box(modifier.width(photoSize + 8.dp), contentAlignment = Alignment.Center) {
        when (PlayerBadgeContent.of(photoUrl, shirtNumber)) {
            PlayerBadgeContent.PHOTO_AND_NUMBER -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PersonPhoto(photoUrl.orEmpty(), "", photoSize, placeholderColor.copy(alpha = 0.15f), placeholderColor)
                Text(
                    shirtNumber.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                    color = numberColor,
                    textAlign = TextAlign.Center
                )
            }
            PlayerBadgeContent.PHOTO -> PersonPhoto(photoUrl.orEmpty(), "", photoSize, placeholderColor.copy(alpha = 0.15f), placeholderColor)
            PlayerBadgeContent.NUMBER -> Text(shirtNumber.toString(), style = numberStyle, color = numberColor, textAlign = TextAlign.Center)
            PlayerBadgeContent.PLACEHOLDER -> PersonPlaceholder(photoSize, placeholderColor)
        }
    }
}

/** A plain person in a circle -- head and shoulders -- for a player with neither a photo nor a number. */
@Composable
fun PersonPlaceholder(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(0f, 0f, w, w)) }
        drawCircle(color.copy(alpha = color.alpha * 0.4f), radius = w / 2)
        clipPath(circle) {
            drawCircle(color, radius = w * 0.19f, center = Offset(w / 2, w * 0.4f))
            drawOval(color, topLeft = Offset(w * 0.17f, w * 0.66f), size = Size(w * 0.66f, w * 0.62f))
        }
    }
}

@Preview
@Composable
private fun PlayerBadgePreview() {
    SquadifyTheme {
        Row(Modifier.background(Color.White).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PlayerBadge(photoUrl = null, shirtNumber = 10)
            PlayerBadge(photoUrl = null, shirtNumber = null)
        }
    }
}
