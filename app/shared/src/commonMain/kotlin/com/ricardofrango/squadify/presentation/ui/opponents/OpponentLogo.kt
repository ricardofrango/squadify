package com.ricardofrango.squadify.presentation.ui.opponents

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon

/**
 * An opponent's logo. Logos can't be uploaded yet, so this is always the placeholder: the initials in a
 * neutral grey circle (design 38b). Never green, which is kept for our own club.
 */
@Composable
fun OpponentLogo(name: String, shortName: String?, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val initials = opponentInitials(name, shortName)
    Box(
        modifier.size(size).background(CarbonGray.copy(alpha = 0.08f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
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
