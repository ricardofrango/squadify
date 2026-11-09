package com.ricardofrango.squadify.presentation.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * The app's standard secondary action, beside or under a [PrimaryLoadingButton]: the same height (58dp)
 * and shape, outlined in `PitchGreen` on the screen's background, with a readable label (the green's,
 * or [DeepCarbon] when [neutral], e.g. Back next to Next) and an optional [icon] before it. Full width
 * unless [modifier] says otherwise (e.g. a `weight` in a row).
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: DrawableResource? = null,
    neutral: Boolean = false
) {
    val color = if (neutral) DeepCarbon else PitchGreen
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(58.dp),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, (if (neutral) DeepCarbon else PitchGreen).copy(alpha = if (enabled) 0.35f else 0.15f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color, disabledContentColor = color.copy(alpha = 0.4f))
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
        }
    }
}

@Preview
@Composable
private fun SecondaryButtonPreview() {
    SquadifyTheme { SecondaryButton(text = "Join with a code", onClick = {}) }
}

@Preview
@Composable
private fun SecondaryButtonNeutralPreview() {
    SquadifyTheme { SecondaryButton(text = "Back", onClick = {}, neutral = true) }
}
