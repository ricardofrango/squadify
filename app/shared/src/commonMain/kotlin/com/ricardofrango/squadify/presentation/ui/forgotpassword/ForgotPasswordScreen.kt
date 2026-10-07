package com.ricardofrango.squadify.presentation.ui.forgotpassword

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
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.presentation.ui.components.FMTopBar
import com.ricardofrango.squadify.presentation.ui.components.PasswordVisibilityToggle
import com.ricardofrango.squadify.presentation.ui.components.PrimaryLoadingButton
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
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
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) { viewModel.done.collect { onPasswordReset(it) } }

    var email by rememberSaveable { mutableStateOf(initialEmail) }
    var code by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val enabled = !state.submitting

    Scaffold(
        topBar = { FMTopBar(title = stringResource(Res.string.title_forgot_password), onNavigateBack = onNavigateBack) }
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
                .background(Color.White)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (state.step) {
                ForgotPasswordStep.EMAIL -> {
                    Intro(stringResource(Res.string.msg_forgot_email_intro))
                    ErrorLine(state.error)
                    Field(
                        value = email,
                        onValueChange = { email = it; viewModel.clearError() },
                        label = stringResource(Res.string.label_email),
                        enabled = enabled,
                        isError = state.error != null,
                        keyboardType = KeyboardType.Email,
                        modifier = Modifier.semantics { contentType = ContentType.Username + ContentType.EmailAddress }
                    )
                    PrimaryLoadingButton(
                        text = Res.string.btn_send_code,
                        submittingText = Res.string.btn_sending_code,
                        isLoading = state.submitting,
                        onClick = { viewModel.sendCode(email) }
                    )
                }
                ForgotPasswordStep.CODE -> {
                    Intro(stringResource(Res.string.msg_forgot_code_intro, state.email))
                    ErrorLine(state.error)
                    Field(
                        value = code,
                        onValueChange = { new -> code = new.filter { it.isDigit() }.take(6); viewModel.clearError() },
                        label = stringResource(Res.string.label_reset_code),
                        enabled = enabled,
                        isError = state.error != null,
                        keyboardType = KeyboardType.NumberPassword,
                        modifier = Modifier.semantics { contentType = ContentType.SmsOtpCode }
                    )
                    Field(
                        value = password,
                        onValueChange = { password = it; viewModel.clearError() },
                        label = stringResource(Res.string.label_new_password_field),
                        supporting = stringResource(Res.string.msg_password_min_length),
                        enabled = enabled,
                        keyboardType = KeyboardType.Password,
                        hidden = !passwordVisible,
                        onToggleHidden = { passwordVisible = !passwordVisible },
                        modifier = Modifier.semantics { contentType = ContentType.NewPassword }
                    )
                    Field(
                        value = confirm,
                        onValueChange = { confirm = it; viewModel.clearError() },
                        label = stringResource(Res.string.label_confirm_new_password_field),
                        enabled = enabled,
                        keyboardType = KeyboardType.Password,
                        hidden = !passwordVisible,
                        modifier = Modifier.semantics { contentType = ContentType.NewPassword }
                    )
                    PrimaryLoadingButton(
                        text = Res.string.btn_set_new_password,
                        submittingText = Res.string.btn_setting_new_password,
                        isLoading = state.submitting,
                        onClick = { viewModel.setNewPassword(code, password, confirm) }
                    )
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        TextButton(onClick = viewModel::resendCode, enabled = enabled && state.resendInSeconds == 0) {
                            Text(
                                if (state.resendInSeconds > 0) stringResource(Res.string.btn_resend_code_in, state.resendInSeconds)
                                else stringResource(Res.string.btn_resend_code),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        TextButton(onClick = { code = ""; viewModel.useDifferentEmail() }, enabled = enabled) {
                            Text(stringResource(Res.string.btn_use_different_email), color = CarbonGray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Intro(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = CarbonGray)
}

/** Reserved space, so an error doesn't push the fields down. */
@Composable
private fun ErrorLine(error: String?) {
    Box(Modifier.fillMaxWidth().heightIn(min = 20.dp), contentAlignment = Alignment.CenterStart) {
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supporting: String? = null,
    hidden: Boolean = false,
    onToggleHidden: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = supporting?.let { { Text(it) } },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        enabled = enabled,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = onToggleHidden?.let { toggle -> { PasswordVisibilityToggle(visible = !hidden, onToggle = toggle) } },
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = DeepCarbon,
            unfocusedTextColor = DeepCarbon,
            // The error state only changes the border and label: the typed text stays dark.
            errorTextColor = DeepCarbon,
            // Disabled while submitting: dimmed, but still readable.
            disabledTextColor = DeepCarbon.copy(alpha = 0.6f),
            disabledBorderColor = Color.Gray.copy(alpha = 0.2f),
            disabledLabelColor = Color.Gray.copy(alpha = 0.8f),
            disabledSupportingTextColor = Color.Gray.copy(alpha = 0.8f),
            disabledTrailingIconColor = Color.Gray.copy(alpha = 0.6f),
            focusedBorderColor = PitchGreen,
            unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f),
            focusedLabelColor = PitchGreen,
            unfocusedLabelColor = Color.Gray
        )
    )
}
