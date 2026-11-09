package com.ricardofrango.squadify.presentation.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.presentation.ui.theme.AlertRed
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme

/**
 * The app's button for deleting something for good (a team, an age group, an opponent): outlined and
 * labelled in [AlertRed], full width. It only asks; the screen confirms with a [ConfirmDialog] first.
 */
@Composable
fun DestructiveButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, AlertRed.copy(alpha = if (enabled) 1f else 0.3f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertRed, disabledContentColor = AlertRed.copy(alpha = 0.4f))
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
    }
}

@Preview
@Composable
private fun DestructiveButtonPreview() {
    SquadifyTheme { DestructiveButton(text = "Delete team", onClick = {}) }
}
