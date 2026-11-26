package com.sportssoftw.squadcall.presentation.ui.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import squadcall.app.shared.generated.resources.Res
import squadcall.app.shared.generated.resources.btn_cancel
import squadcall.app.shared.generated.resources.btn_confirm

/**
 * A title+body confirmation in the app's dialog look ([SquadcallDialog]): Cancel outlined on the left,
 * [confirmLabel] filled on the right, red when [destructive] (the default), the club's colour otherwise.
 */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = stringResource(Res.string.btn_confirm),
    dismissLabel: String = stringResource(Res.string.btn_cancel),
    destructive: Boolean = true
) {
    SquadcallDialog(title = title, body = body, onDismiss = onDismiss) {
        DialogButtons(confirmLabel, onConfirm, onDismiss, dismissLabel, destructive)
    }
}
