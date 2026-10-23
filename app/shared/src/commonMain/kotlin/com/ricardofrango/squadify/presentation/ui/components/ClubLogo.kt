package com.ricardofrango.squadify.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ricardofrango.squadify.domain.config.AppConfig
import org.koin.compose.koinInject

/**
 * A club's logo, or its initials when it has none (or the image can't load). [logoUrl] is the server
 * path (`/clubs/{clubId}/logo/{imageId}.png`); it changes with every upload, so Coil's cache never shows
 * an old logo. Loaded through the app's authenticated client (see App.kt).
 */
@Composable
fun ClubLogo(
    logoUrl: String?,
    initials: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    shape: Shape = CircleShape,
    background: Color,
    initialsColor: Color,
    contentScale: ContentScale = ContentScale.Fit,
    initialsStyle: TextStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
) {
    var failed by remember(logoUrl) { mutableStateOf(false) }
    Box(modifier.size(size).clip(shape).background(background), contentAlignment = Alignment.Center) {
        if (logoUrl != null && !failed) {
            val baseUrl = koinInject<AppConfig>().baseUrl
            AsyncImage(
                model = baseUrl + logoUrl,
                contentDescription = null,
                contentScale = contentScale,
                onError = { failed = true },
                modifier = Modifier.size(size)
            )
        } else {
            Text(initials, color = initialsColor, style = initialsStyle)
        }
    }
}
