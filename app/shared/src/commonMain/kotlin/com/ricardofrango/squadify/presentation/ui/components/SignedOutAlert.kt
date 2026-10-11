package com.ricardofrango.squadify.presentation.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.DialogProperties
import com.ricardofrango.squadify.data.remote.ApiException
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import org.jetbrains.compose.resources.stringResource
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.btn_ok
import squadify.app.shared.generated.resources.msg_signed_out_wrong_passwords
import squadify.app.shared.generated.resources.title_signed_out

/** The server's "current password is incorrect" (Change password / Change email): a plain form error. */
fun Throwable.isWrongCurrentPassword() = this is ApiException && code == "AUTH_003"

/** The third wrong current password in a row: the server has signed the user out everywhere. */
fun Throwable.isSignedOutForWrongPasswords() = this is ApiException && code == "AUTH_004"

/**
 * Shown after the third wrong current password: the session is over, so the only way out is OK, which
 * signs out locally and goes to Sign in ([onConfirm]). Not dismissable by tapping outside or Back.
 */
@Composable
fun SignedOutAlert(onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        containerColor = Color.White,
        titleContentColor = DeepCarbon,
        textContentColor = DeepCarbon.copy(alpha = 0.75f),
        title = { Text(stringResource(Res.string.title_signed_out)) },
        text = { Text(stringResource(Res.string.msg_signed_out_wrong_passwords)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(Res.string.btn_ok), color = PitchGreen, fontWeight = FontWeight.Bold) }
        }
    )
}
