package com.ricardofrango.squadify.presentation.ui.opponents

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ricardofrango.squadify.domain.config.AppConfig
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import org.koin.compose.koinInject

/**
 * An opponent's logo, or its initials in a neutral grey circle when it has none or the image can't load
 * (design 38b; never green, which is kept for our own club). [logoUrl] is the server path
 * (`/opponents/{opponentId}/logo/{imageId}.png`), loaded through the app's authenticated client;
 * [picked] is an image chosen on the form and not uploaded yet, shown in its place.
 */
@Composable
fun OpponentLogo(
    name: String,
    shortName: String?,
    logoUrl: String? = null,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
    picked: ByteArray? = null
) {
    val initials = opponentInitials(name, shortName)
    val model: Any? = picked ?: logoUrl?.let { koinInject<AppConfig>().baseUrl + it }
    var failed by remember(model) { mutableStateOf(false) }
    Box(
        modifier.size(size).clip(CircleShape).background(CarbonGray.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center
    ) {
        if (model != null && !failed) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onError = { failed = true },
                modifier = Modifier.size(size)
            )
        } else {
            Text(
                initials,
                color = DeepCarbon.copy(alpha = 0.7f),
                fontWeight = FontWeight.Bold,
                // Up to 5 letters have to fit the circle.
                fontSize = (size.value * if (initials.length > 3) 0.24f else 0.32f).sp,
                maxLines = 1
            )
        }
    }
}
