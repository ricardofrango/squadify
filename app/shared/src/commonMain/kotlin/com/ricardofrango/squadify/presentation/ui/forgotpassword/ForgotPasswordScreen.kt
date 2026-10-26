package com.ricardofrango.squadify.presentation.ui.forgotpassword

import com.ricardofrango.squadify.presentation.ui.components.SectionCard
import com.ricardofrango.squadify.presentation.ui.theme.ScreenBackground
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricardofrango.squadify.presentation.ui.components.SquadifyTopBar
import com.ricardofrango.squadify.presentation.ui.components.FormErrorLine
import com.ricardofrango.squadify.presentation.ui.components.FormIntro
import com.ricardofrango.squadify.presentation.ui.components.FormTextField
import com.ricardofrango.squadify.presentation.ui.components.PasswordVisibilityToggle
import com.ricardofrango.squadify.presentation.ui.components.PrimaryLoadingButton
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.screenContentPadding
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.btn_resend_code
import squadify.app.shared.generated.resources.btn_resend_code_in
import squadify.app.shared.generated.resources.btn_send_code
import squadify.app.shared.generated.resources.btn_sending_code
import squadify.app.shared.generated.resources.btn_set_new_password
import squadify.app.shared.generated.resources.btn_setting_new_password
import squadify.app.shared.generated.resources.btn_use_different_email
import squadify.app.shared.generated.resources.label_confirm_new_password_field
import squadify.app.shared.generated.resources.label_email
import squadify.app.shared.generated.resources.label_new_password_field
import squadify.app.shared.generated.resources.label_reset_code
import squadify.app.shared.generated.resources.msg_forgot_code_intro
import squadify.app.shared.generated.resources.msg_forgot_email_intro
import squadify.app.shared.generated.resources.msg_password_min_length
import squadify.app.shared.generated.resources.title_forgot_password

/**
 * Forgot password, reached from Login. Step 1: the email (prefilled from Login) and "Send code".
 * Step 2: the 6-digit code from the email, the new password twice, and "Set new password"; plus
 * "Send a new code" (after a 60-second cooldown) and "Use a different email". On success it returns
 * to Login with the email filled in.
 */
@Composable
fun ForgotPasswordScreen(
    initialEmail: String,
    onNavigateBack: () -> Unit,
    onPasswordReset: (email: String) -> Unit,
    viewModel: ForgotPasswordViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.done.collect { onPasswordReset(it) } }

    ForgotPasswordContent(
        state = state,
        initialEmail = initialEmail,
        onNavigateBack = onNavigateBack,
        onClearError = viewModel::clearError,
        onSendCode = viewModel::sendCode,
        onSetNewPassword = viewModel::setNewPassword,
        onResendCode = viewModel::resendCode,
        onUseDifferentEmail = viewModel::useDifferentEmail
    )
}

/** Forgot password's layout, without the ViewModel, so it can be previewed. */
@Composable
private fun ForgotPasswordContent(
    state: ForgotPasswordUiState,
    initialEmail: String,
    onNavigateBack: () -> Unit,
    onClearError: () -> Unit,
    onSendCode: (String) -> Unit,
    onSetNewPassword: (code: String, password: String, confirm: String) -> Unit,
    onResendCode: () -> Unit,
    onUseDifferentEmail: () -> Unit
) {
    var email by rememberSaveable { mutableStateOf(initialEmail) }
    var code by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val enabled = !state.submitting

    Scaffold(
        topBar = { SquadifyTopBar(title = stringResource(Res.string.title_forgot_password), onNavigateBack = onNavigateBack) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                // The Scaffold's padding already includes the navigation bar. Without consuming it,
                // imePadding() would add the keyboard's full inset (which also covers the navigation
                // bar) on top, leaving an empty strip the height of the navigation bar above the keyboard.
                .consumeWindowInsets(padding)
                .imePadding()
                .background(ScreenBackground)
                .verticalScroll(rememberScrollState())
                .screenContentPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionCard(spacing = 16.dp) {
                when (state.step) {
                    ForgotPasswordStep.EMAIL -> {
                        FormIntro(stringResource(Res.string.msg_forgot_email_intro))
                        FormErrorLine(state.error)
                        FormTextField(
                            value = email,
                            onValueChange = { email = it; onClearError() },
                            label = stringResource(Res.string.label_email),
                            enabled = enabled,
                            isError = state.error != null,
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Send,
                            onImeAction = { onSendCode(email) },
                            modifier = Modifier.semantics { contentType = ContentType.Username + ContentType.EmailAddress }
                        )
                        PrimaryLoadingButton(
                            text = Res.string.btn_send_code,
                            submittingText = Res.string.btn_sending_code,
                            isLoading = state.submitting,
                            onClick = { onSendCode(email) }
                        )
                    }
                    ForgotPasswordStep.CODE -> {
                        FormIntro(stringResource(Res.string.msg_forgot_code_intro, state.email))
                        FormErrorLine(state.error)
                        FormTextField(
                            value = code,
                            onValueChange = { new -> code = new.filter { it.isDigit() }.take(6); onClearError() },
                            label = stringResource(Res.string.label_reset_code),
                            enabled = enabled,
                            isError = state.error != null,
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Next,
                            modifier = Modifier.semantics { contentType = ContentType.SmsOtpCode }
                        )
                        FormTextField(
                            value = password,
                            onValueChange = { password = it; onClearError() },
                            label = stringResource(Res.string.label_new_password_field),
                            supporting = stringResource(Res.string.msg_password_min_length),
                            enabled = enabled,
                            keyboardType = KeyboardType.Password,
                            hidden = !passwordVisible,
                            onToggleHidden = { passwordVisible = !passwordVisible },
                            imeAction = ImeAction.Next,
                            modifier = Modifier.semantics { contentType = ContentType.NewPassword }
                        )
                        FormTextField(
                            value = confirm,
                            onValueChange = { confirm = it; onClearError() },
                            label = stringResource(Res.string.label_confirm_new_password_field),
                            enabled = enabled,
                            keyboardType = KeyboardType.Password,
                            hidden = !passwordVisible,
                            imeAction = ImeAction.Done,
                            onImeAction = { onSetNewPassword(code, password, confirm) },
                            modifier = Modifier.semantics { contentType = ContentType.NewPassword }
                        )
                        PrimaryLoadingButton(
                            text = Res.string.btn_set_new_password,
                            submittingText = Res.string.btn_setting_new_password,
                            isLoading = state.submitting,
                            onClick = { onSetNewPassword(code, password, confirm) }
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
}

@Preview
@Composable
private fun ForgotPasswordEmailPreview() {
    SquadifyTheme { ForgotPasswordContent(ForgotPasswordUiState(), "ana@clube.pt", {}, {}, {}, { _, _, _ -> }, {}, {}) }
}

@Preview
@Composable
private fun ForgotPasswordCodePreview() {
    SquadifyTheme { ForgotPasswordContent(ForgotPasswordUiState(step = ForgotPasswordStep.CODE, email = "ana@clube.pt", resendInSeconds = 42), "ana@clube.pt", {}, {}, {}, { _, _, _ -> }, {}, {}) }
}
