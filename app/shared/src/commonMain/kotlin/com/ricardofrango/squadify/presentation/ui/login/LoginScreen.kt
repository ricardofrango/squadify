package com.ricardofrango.squadify.presentation.ui.login

import com.ricardofrango.squadify.presentation.credentials.rememberCredentialStore
import com.ricardofrango.squadify.presentation.ui.components.submitKeyboardActions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricardofrango.squadify.presentation.ui.components.PasswordVisibilityToggle
import com.ricardofrango.squadify.presentation.ui.components.PrimaryLoadingButton
import com.ricardofrango.squadify.presentation.ui.components.SquadifyMark
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import squadify.app.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun LoginScreen(
    onNavigateToRegister: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    onNavigateToOnlyClub: (String) -> Unit,
    onNavigateToForgotPassword: (email: String) -> Unit,
    // Set when coming back from Forgot password: the email is filled in and a confirmation shown.
    passwordResetEmail: String? = null,
    viewModel: LoginViewModel = koinViewModel()
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()

    // Lets the platform password manager offer to save the entered credentials once login succeeds
    // (null on targets without an autofill service, e.g. desktop/web/iOS).
    val autofillManager = LocalAutofillManager.current
    // Android's Credential Manager: the "save password?" sheet after signing in, and a saved account
    // to sign in with when the screen opens. Elsewhere it does nothing and autofill does the saving.
    val credentialStore = rememberCredentialStore()

    // Local UI State for input fields
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(passwordResetEmail) {
        if (passwordResetEmail != null) { email = passwordResetEmail; password = "" }
    }
    // Offers a saved account on a fresh, empty screen (not after a reset, or once something's typed).
    LaunchedEffect(Unit) {
        if (passwordResetEmail == null && email.isEmpty() && password.isEmpty()) {
            credentialStore.pick()?.let { email = it.email; password = it.password }
        }
    }
    suspend fun offerToSavePassword() {
        if (credentialStore.isAvailable) credentialStore.save(email, password) else autofillManager?.commit()
    }

    // Observe Navigation Events
    LaunchedEffect(viewModel.navigationEvent) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                // The "save password?" sheet first, so it isn't cut off by leaving this screen.
                LoginNavigationEvent.NavigateToDashboard -> {
                    offerToSavePassword()
                    onNavigateToDashboard()
                }
                is LoginNavigationEvent.NavigateToOnlyClub -> {
                    offerToSavePassword()
                    onNavigateToOnlyClub(event.clubId)
                }
            }
        }
    }

    LoginContent(
        uiState = uiState,
        email = email,
        password = password,
        passwordVisible = passwordVisible,
        passwordResetEmail = passwordResetEmail,
        onEmailChange = { email = it },
        onPasswordChange = { password = it },
        onTogglePasswordVisible = { passwordVisible = !passwordVisible },
        onLogin = { viewModel.onLoginClicked(email, password) },
        onForgotPassword = { onNavigateToForgotPassword(email) },
        onRegister = onNavigateToRegister
    )
}

/** The sign-in screen's layout, without the ViewModel, so it can be previewed. */
@Composable
private fun LoginContent(
    uiState: LoginUiState,
    email: String,
    password: String,
    passwordVisible: Boolean,
    passwordResetEmail: String?,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisible: () -> Unit,
    onLogin: () -> Unit,
    onForgotPassword: () -> Unit,
    onRegister: () -> Unit
) {
    val errorMessage = (uiState as? LoginUiState.Idle)?.error
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(CarbonGray, DeepCarbon)
                )
            )
            .statusBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Section -- the same "18a" mark as the splash/app icon, so the hand-off from
            // launch to login reads as one continuous carbon surface (design canvas "19B"). Sized
            // to its own content (no weighted spacer above the card) so it can't blow past the
            // fixed budget a fixed-fraction card would otherwise reserve for it.
            Spacer(modifier = Modifier.height(20.dp))
    
            SquadifyMark(size = 64.dp)
    
            Spacer(modifier = Modifier.height(10.dp))
    
            Text(
                text = stringResource(Res.string.app_name),
                style = MaterialTheme.typography.displaySmall.copy(
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            )
    
            Spacer(modifier = Modifier.height(6.dp))
    
            Text(
                text = stringResource(Res.string.msg_precision_management),
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = Color.White.copy(alpha = 0.7f)
                )
            )
    
            Spacer(modifier = Modifier.height(20.dp))
    
            // Login Card -- takes all remaining height, so it isn't pinned to a fixed fraction of
            // the screen that the header above could overflow.
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                color = Color.White,
                shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 32.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(Res.string.btn_login),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = CarbonGray
                        ),
                        modifier = Modifier.align(Alignment.Start)
                    )
    
                    Spacer(modifier = Modifier.height(8.dp))
    
                    // Error Message, reserved space between the title and the email field so a
                    // failed login doesn't shift the fields down underneath the user's fingers.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (errorMessage != null) {
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else if (passwordResetEmail != null) {
                            Text(
                                text = stringResource(Res.string.msg_password_reset_done),
                                color = PitchGreen,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
    
                    OutlinedTextField(
                        value = email,
                        onValueChange = onEmailChange,
                        label = { Text(stringResource(Res.string.label_email)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentType = ContentType.Username + ContentType.EmailAddress },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        shape = RoundedCornerShape(16.dp),
                        isError = errorMessage != null,
                        enabled = uiState !is LoginUiState.Loading,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = DeepCarbon,
                            unfocusedTextColor = DeepCarbon,
                            errorTextColor = DeepCarbon,
                            disabledTextColor = DeepCarbon.copy(alpha = 0.6f),
                            disabledBorderColor = Color.Gray.copy(alpha = 0.2f),
                            disabledLabelColor = Color.Gray.copy(alpha = 0.8f),
                            disabledTrailingIconColor = Color.Gray.copy(alpha = 0.6f),
                            focusedBorderColor = PitchGreen,
                            unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f),
                            focusedLabelColor = PitchGreen,
                            unfocusedLabelColor = Color.Gray
                        )
                    )
    
                    Spacer(modifier = Modifier.height(8.dp))
    
                    OutlinedTextField(
                        value = password,
                        onValueChange = onPasswordChange,
                        label = { Text(stringResource(Res.string.label_password)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentType = ContentType.Password },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = submitKeyboardActions { onLogin() },
                        trailingIcon = {
                            PasswordVisibilityToggle(
                                visible = passwordVisible,
                                onToggle = onTogglePasswordVisible
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        isError = errorMessage != null,
                        enabled = uiState !is LoginUiState.Loading,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = DeepCarbon,
                            unfocusedTextColor = DeepCarbon,
                            errorTextColor = DeepCarbon,
                            disabledTextColor = DeepCarbon.copy(alpha = 0.6f),
                            disabledBorderColor = Color.Gray.copy(alpha = 0.2f),
                            disabledLabelColor = Color.Gray.copy(alpha = 0.8f),
                            disabledTrailingIconColor = Color.Gray.copy(alpha = 0.6f),
                            focusedBorderColor = PitchGreen,
                            unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f),
                            focusedLabelColor = PitchGreen,
                            unfocusedLabelColor = Color.Gray
                        )
                    )
    
                    TextButton(
                        onClick = onForgotPassword,
                        modifier = Modifier.align(Alignment.End),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                        enabled = uiState !is LoginUiState.Loading
                    ) {
                        Text(
                            text = stringResource(Res.string.msg_forgot_password),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = CarbonGray,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
    
                    Spacer(modifier = Modifier.height(8.dp))
    
                    PrimaryLoadingButton(
                        text = Res.string.btn_login,
                        submittingText = Res.string.btn_loging_in,
                        isLoading = uiState is LoginUiState.Loading,
                        onClick = onLogin
                    )
    
                    Spacer(modifier = Modifier.height(24.dp))
    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = Color.Gray.copy(alpha = 0.25f))
                        Text(
                            text = stringResource(Res.string.label_or_divider),
                            modifier = Modifier.padding(horizontal = 12.dp),
                            style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray)
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = Color.Gray.copy(alpha = 0.25f))
                    }
    
                    Spacer(modifier = Modifier.height(24.dp))
    
                    OutlinedButton(
                        onClick = onRegister,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        enabled = uiState !is LoginUiState.Loading,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CarbonGray)
                    ) {
                        Text(
                            text = stringResource(Res.string.btn_create_account),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun LoginPreview() {
    SquadifyTheme {
        LoginContent(LoginUiState.Idle(), "ana@clube.pt", "", false, null, {}, {}, {}, {}, {}, {})
    }
}

@Preview
@Composable
private fun LoginErrorPreview() {
    SquadifyTheme {
        LoginContent(LoginUiState.Idle(error = "Invalid email or password"), "ana@clube.pt", "secret12", false, null, {}, {}, {}, {}, {}, {})
    }
}

@Preview
@Composable
private fun LoginSigningInPreview() {
    SquadifyTheme {
        LoginContent(LoginUiState.Loading, "ana@clube.pt", "secret12", false, null, {}, {}, {}, {}, {}, {})
    }
}
