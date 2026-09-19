package com.ricardofrango.squadify.presentation.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.btn_cancel
import squadify.app.shared.generated.resources.btn_confirm

/** A plain title+body confirmation dialog for a destructive/irreversible action. */
@Composable
fun ConfirmDialog(title: String, body: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(Res.string.btn_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.btn_cancel)) } }
    )
}
