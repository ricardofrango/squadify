package com.ricardofrango.squadify.presentation.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.ricardofrango.squadify.presentation.ui.theme.AlertRed
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import org.jetbrains.compose.resources.stringResource
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.btn_cancel
import squadify.app.shared.generated.resources.btn_confirm

/**
 * A plain title+body confirmation dialog for a destructive/irreversible action. White with dark text
 * and a bold [confirmColor] Confirm, like the app's other dialogs, whatever the phone's dark mode: the
 * theme's colours made it dark with dark buttons over the live match.
 */
@Composable
fun ConfirmDialog(title: String, body: String, onConfirm: () -> Unit, onDismiss: () -> Unit, confirmColor: Color = AlertRed) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        titleContentColor = CarbonGray,
        textContentColor = DeepCarbon.copy(alpha = 0.7f),
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(Res.string.btn_confirm), color = confirmColor, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.btn_cancel), color = CarbonGray) } }
    )
}
