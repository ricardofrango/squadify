package com.ricardofrango.squadify.presentation.ui.changeemail

import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import androidx.compose.ui.tooling.preview.Preview
import com.ricardofrango.squadify.presentation.ui.components.SignedOutAlert
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricardofrango.squadify.presentation.ui.components.SquadifyTopBar
import com.ricardofrango.squadify.presentation.ui.components.FormErrorLine
import com.ricardofrango.squadify.presentation.ui.components.FormIntro
import com.ricardofrango.squadify.presentation.ui.components.FormTextField
import com.ricardofrango.squadify.presentation.ui.components.PrimaryLoadingButton
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyClubTheme
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.btn_change_email
import squadify.app.shared.generated.resources.btn_confirm_email_change
import squadify.app.shared.generated.resources.btn_resend_code
import squadify.app.shared.generated.resources.btn_resend_code_in
import squadify.app.shared.generated.resources.btn_send_code
import squadify.app.shared.generated.resources.btn_sending_code
import squadify.app.shared.generated.resources.btn_confirming
import squadify.app.shared.generated.resources.btn_use_different_email
import squadify.app.shared.generated.resources.label_current_password_field
import squadify.app.shared.generated.resources.label_new_email
import squadify.app.shared.generated.resources.label_reset_code
import squadify.app.shared.generated.resources.msg_change_email_code_intro
import squadify.app.shared.generated.resources.msg_change_email_intro

/**
 * Change email, reached from Account. Step 1: the new address and the current password, "Send code"
 * (the code goes to the new address). Step 2: the code, "Confirm", plus "Send a new code" (after the
 * 60-second cooldown) and "Use a different email". On success it returns to Account with the new address.
 */
@Composable
fun ChangeEmailScreen(
    currentEmail: String?,
    onNavigateBack: () -> Unit,
    onEmailChanged: (newEmail: String) -> Unit,
    onSignedOut: () -> Unit,
    viewModel: ChangeEmailViewModel = koinViewModel()
) {
    SquadifyClubTheme {
        val state by viewModel.state.collectAsStateWithLifecycle()
        LaunchedEffect(viewModel) { viewModel.done.collect { onEmailChanged(it) } }
        LaunchedEffect(viewModel) { viewModel.signOutDone.collect { onSignedOut() } }
        if (state.signedOut) SignedOutAlert(onConfirm = viewModel::finishSignOut)

        ChangeEmailContent(
            state = state,
            onNavigateBack = onNavigateBack,
            onClearError = viewModel::clearError,
            onSendCode = { newEmail, password -> viewModel.sendCode(newEmail, password, currentEmail) },
            onConfirm = viewModel::confirm,
            onResendCode = viewModel::resendCode,
            onUseDifferentEmail = viewModel::useDifferentEmail
        )
    }
}

/** Change email's layout, without the ViewModel, so it can be previewed. */
@Composable
private fun ChangeEmailContent(
    state: ChangeEmailUiState,
    onNavigateBack: () -> Unit,
    onClearError: () -> Unit,
    onSendCode: (newEmail: String, password: String) -> Unit,
    onConfirm: (code: String) -> Unit,
    onResendCode: () -> Unit,
    onUseDifferentEmail: () -> Unit
) {
    var newEmail by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    val enabled = !state.submitting
    val sendCode = { onSendCode(newEmail, password) }

    Scaffold(
        topBar = { SquadifyTopBar(title = stringResource(Res.string.btn_change_email), onNavigateBack = onNavigateBack) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                // See ForgotPasswordScreen: without this, the navigation bar is counted twice above the keyboard.
                .consumeWindowInsets(padding)
                .imePadding()
                .background(Color.White)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (state.step) {
                ChangeEmailStep.EMAIL -> {
                    FormIntro(stringResource(Res.string.msg_change_email_intro))
                    FormErrorLine(state.error)
                    FormTextField(
                        value = newEmail,
                        onValueChange = { newEmail = it; onClearError() },
                        label = stringResource(Res.string.label_new_email),
                        enabled = enabled,
                        isError = state.error != null,
                        keyboardType = KeyboardType.Email,
                        modifier = Modifier.semantics { contentType = ContentType.EmailAddress }
                    )
                    FormTextField(
                        value = password,
                        onValueChange = { password = it; onClearError() },
                        label = stringResource(Res.string.label_current_password_field),
                        enabled = enabled,
                        keyboardType = KeyboardType.Password,
                        hidden = !passwordVisible,
                        onToggleHidden = { passwordVisible = !passwordVisible },
                        imeAction = ImeAction.Send,
                        onImeAction = sendCode,
                        modifier = Modifier.semantics { contentType = ContentType.Password }
                    )
                    PrimaryLoadingButton(
                        text = Res.string.btn_send_code,
                        submittingText = Res.string.btn_sending_code,
                        isLoading = state.submitting,
                        onClick = sendCode
                    )
                }
                ChangeEmailStep.CODE -> {
                    FormIntro(stringResource(Res.string.msg_change_email_code_intro, state.newEmail))
                    FormErrorLine(state.error)
                    FormTextField(
                        value = code,
                        onValueChange = { new -> code = new.filter { it.isDigit() }.take(6); onClearError() },
                        label = stringResource(Res.string.label_reset_code),
                        enabled = enabled,
                        isError = state.error != null,
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done,
                        onImeAction = { onConfirm(code) },
                        modifier = Modifier.semantics { contentType = ContentType.SmsOtpCode }
                    )
                    PrimaryLoadingButton(
                        text = Res.string.btn_confirm_email_change,
                        submittingText = Res.string.btn_confirming,
                        isLoading = state.submitting,
                        onClick = { onConfirm(code) }
                    )
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        TextButton(onClick = onResendCode, enabled = enabled && state.resendInSeconds == 0) {
                            Text(
                                if (state.resendInSeconds > 0) stringResource(Res.string.btn_resend_code_in, state.resendInSeconds)
                                else stringResource(Res.string.btn_resend_code),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        TextButton(onClick = { code = ""; onUseDifferentEmail() }, enabled = enabled) {
                            Text(stringResource(Res.string.btn_use_different_email), color = CarbonGray)
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun ChangeEmailPreview() {
    SquadifyTheme { ChangeEmailContent(ChangeEmailUiState(), {}, {}, { _, _ -> }, {}, {}, {}) }
}

@Preview
@Composable
private fun ChangeEmailCodePreview() {
    SquadifyTheme { ChangeEmailContent(ChangeEmailUiState(step = ChangeEmailStep.CODE, newEmail = "ana.nova@clube.pt", resendInSeconds = 42), {}, {}, { _, _ -> }, {}, {}, {}) }
}
