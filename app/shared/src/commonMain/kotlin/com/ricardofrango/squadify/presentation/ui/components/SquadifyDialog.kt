package com.ricardofrango.squadify.presentation.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.ricardofrango.squadify.presentation.ui.theme.AlertRed
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import org.jetbrains.compose.resources.stringResource
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.btn_cancel

/**
 * The app's one dialog look (the match report's, 24a): white and rounded, a bold title, the body in
 * grey, then [buttons]: usually [DialogButtons], an outlined Cancel on the left and the action filled
 * on the right. Its own colours, so the phone's dark mode doesn't turn it dark. [content] goes between
 * the body and the buttons (a list to pick from); the text part scrolls when it's taller than the
 * window, so the buttons stay on screen on a phone lying down.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SquadifyDialog(
    title: String,
    onDismiss: () -> Unit,
    body: String? = null,
    properties: DialogProperties = DialogProperties(),
    content: (@Composable ColumnScope.() -> Unit)? = null,
    buttons: @Composable () -> Unit
) {
    BasicAlertDialog(onDismissRequest = onDismiss, properties = properties) {
        Surface(shape = RoundedCornerShape(20.dp), color = Color.White) {
            Column(Modifier.padding(24.dp)) {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black), color = DeepCarbon)
                    if (body != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(body, style = MaterialTheme.typography.bodyMedium, color = StatsSecondaryText)
                    }
                    if (content != null) {
                        Spacer(Modifier.height(10.dp))
                        content()
                    }
                }
                Spacer(Modifier.height(20.dp))
                buttons()
            }
        }
    }
}

/**
 * [dismissLabel] outlined on the left, [confirmLabel] filled on the right, the same width: the club's
 * colour, or red when the action is [destructive] (deletes, removes, ends or discards something).
 */
@Composable
fun DialogButtons(
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = stringResource(Res.string.btn_cancel),
    destructive: Boolean = false,
    confirmEnabled: Boolean = true
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DialogSecondaryButton(dismissLabel, Modifier.weight(1f), onClick = onDismiss)
        DialogPrimaryButton(confirmLabel, Modifier.weight(1f), destructive = destructive, enabled = confirmEnabled, onClick = onConfirm)
    }
}

/** A dialog's action, filled: the club's colour, or red when [destructive]. */
@Composable
fun DialogPrimaryButton(label: String, modifier: Modifier, destructive: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (destructive) AlertRed else PitchGreen,
            contentColor = Color.White,
            disabledContainerColor = CarbonGray.copy(alpha = 0.12f),
            disabledContentColor = CarbonGray.copy(alpha = 0.38f)
        ),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
    ) { Text(label, fontWeight = FontWeight.Black, textAlign = TextAlign.Center) }
}

/** A dialog's other choice (Cancel, Keep editing), outlined on white; red text when [destructive]. */
@Composable
fun DialogSecondaryButton(label: String, modifier: Modifier, destructive: Boolean = false, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
        border = BorderStroke(1.5.dp, if (destructive) AlertRed.copy(alpha = 0.4f) else CarbonGray.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Text(label, color = if (destructive) AlertRed else CarbonGray, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Preview
@Composable
private fun SquadifyDialogPreview() {
    SquadifyTheme {
        SquadifyDialog(title = "End the game?", body = "This ends the game. You can still edit events afterwards.", onDismiss = {}) {
            DialogButtons(confirmLabel = "Confirm", onConfirm = {}, onDismiss = {}, destructive = true)
        }
    }
}
