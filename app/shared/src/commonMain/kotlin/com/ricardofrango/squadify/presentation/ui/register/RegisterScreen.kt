package com.ricardofrango.squadify.presentation.ui.register

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.ricardofrango.squadify.presentation.credentials.rememberCredentialStore
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.text.KeyboardActions
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
import com.ricardofrango.squadify.presentation.ui.components.readableWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricardofrango.squadify.presentation.ui.components.SquadifyTopBar
import com.ricardofrango.squadify.presentation.ui.components.PasswordVisibilityToggle
import com.ricardofrango.squadify.presentation.ui.components.PrimaryLoadingButton
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.ScreenBackground
import com.ricardofrango.squadify.presentation.ui.theme.screenContentPadding
import squadify.app.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private val SectionShape = RoundedCornerShape(20.dp)
private val FieldShape = RoundedCornerShape(16.dp)
private const val PASSWORD_STRENGTH_SEGMENTS = 4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    viewModel: RegisterViewModel = koinViewModel()
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    // The email and password last sent, so the new account can be offered to the password manager.
    var lastAttempt by remember { mutableStateOf<Pair<String, String>?>(null) }
    val credentialStore = rememberCredentialStore()

    // Observe Navigation Events
    LaunchedEffect(viewModel.navigationEvent) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                RegisterNavigationEvent.NavigateBackToLogin -> onNavigateBack()
                RegisterNavigationEvent.NavigateToDashboard -> {
                    lastAttempt?.let { (email, password) -> credentialStore.save(email, password) }
                    onNavigateToDashboard()
                }
            }
        }
    }

    RegisterContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onRegister = { firstName, lastName, email, phone, password, confirmPassword ->
            lastAttempt = email.trim() to password
            viewModel.onRegisterClicked(firstName, lastName, email, phone, password, confirmPassword)
        }
    )
}

/** One white, rounded card grouping related fields under a small caps section label. */
@Composable
private fun FormSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = SectionShape,
        color = Color.White,
        border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = CarbonGray.copy(alpha = 0.5f)
            )
            content()
        }
    }
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text,
    // Set on the last field only: its keyboard key creates the account, like the button. Every other
    // field's key moves to the next one.
    onSubmit: (() -> Unit)? = null
) {
    var passwordVisible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        visualTransformation = if (!isPassword || passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = if (isPassword) {
            {
                PasswordVisibilityToggle(
                    visible = passwordVisible,
                    onToggle = { passwordVisible = !passwordVisible }
                )
            }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = if (onSubmit != null) ImeAction.Done else ImeAction.Next),
        keyboardActions = onSubmit?.let { submitKeyboardActions(it) } ?: KeyboardActions.Default,
        shape = FieldShape,
        isError = isError,
        enabled = enabled,
        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White, disabledContainerColor = Color.White, 
            focusedTextColor = DeepCarbon,
            unfocusedTextColor = DeepCarbon,
            focusedBorderColor = PitchGreen,
            unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f),
            focusedLabelColor = PitchGreen,
            unfocusedLabelColor = Color.Gray
        )
    )
}

/** A 4-segment strength meter, filled left-to-right by how many of a handful of simple
 * complexity checks the password passes. Purely a visual nudge -- the server is the source of
 * truth for whether a password is actually acceptable. */
@Composable
private fun PasswordStrengthBar(password: String) {
    val filledSegments = remember(password) { passwordStrengthSegments(password) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(PASSWORD_STRENGTH_SEGMENTS) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .background(
                        color = if (index < filledSegments) PitchGreen else Color.Gray.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

private fun passwordStrengthSegments(password: String): Int {
    if (password.isEmpty()) return 0
    var score = 1
    if (password.length >= 8) score++
    if (password.any { it.isDigit() }) score++
    if (password.length >= 12 || password.any { !it.isLetterOrDigit() }) score++
    return score.coerceIn(1, PASSWORD_STRENGTH_SEGMENTS)
}

/** Register's layout, without the ViewModel, so it can be previewed. */
@Composable
private fun RegisterContent(
    uiState: RegisterUiState,
    onNavigateBack: () -> Unit,
    onRegister: (firstName: String, lastName: String, email: String, phone: String, password: String, confirmPassword: String) -> Unit
) {
    // Local UI State for form fields
    var firstName by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }

    val errorMessage = (uiState as? RegisterUiState.Idle)?.error
    val enabled = uiState !is RegisterUiState.Loading

    Scaffold(
        topBar = {
            SquadifyTopBar(
                title = stringResource(Res.string.title_register),
                onNavigateBack = onNavigateBack
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(ScreenBackground)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .readableWidth()
                    .screenContentPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(Res.string.btn_register),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = CarbonGray
                    )
                )

                // Only when there is one: an always-reserved line left a wide gap under the title.
                // It appears after tapping Register, at the bottom, so the fields moving isn't a problem.
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                FormSection(title = stringResource(Res.string.label_section_your_name)) {
                    FormField(
                        value = firstName,
                        onValueChange = { firstName = it },
                        label = stringResource(Res.string.label_first_name),
                        isError = errorMessage != null,
                        enabled = enabled
                    )
                    FormField(
                        value = lastName,
                        onValueChange = { lastName = it },
                        label = stringResource(Res.string.label_last_name),
                        isError = errorMessage != null,
                        enabled = enabled
                    )
                }

                FormSection(title = stringResource(Res.string.label_section_how_we_reach_you)) {
                    FormField(
                        value = email,
                        onValueChange = { email = it },
                        label = stringResource(Res.string.label_email),
                        isError = errorMessage != null,
                        enabled = enabled,
                        keyboardType = KeyboardType.Email
                    )
                    FormField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = stringResource(Res.string.label_phone),
                        isError = errorMessage != null,
                        enabled = enabled,
                        keyboardType = KeyboardType.Phone
                    )
                }

                FormSection(title = stringResource(Res.string.label_section_password)) {
                    FormField(
                        value = password,
                        onValueChange = { password = it },
                        label = stringResource(Res.string.label_password),
                        isError = errorMessage != null,
                        enabled = enabled,
                        isPassword = true
                    )
                    PasswordStrengthBar(password)
                    FormField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = stringResource(Res.string.label_confirm_password),
                        isError = errorMessage != null,
                        enabled = enabled,
                        isPassword = true,
                        onSubmit = { onRegister(firstName, lastName, email, phone, password, confirmPassword) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                PrimaryLoadingButton(
                    text = Res.string.btn_register,
                    submittingText = Res.string.btn_registering,
                    isLoading = uiState is RegisterUiState.Loading,
                    onClick = {
                        onRegister(
                            firstName, lastName, email, phone, password, confirmPassword
                        )
                    }
                )

                TextButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled
                ) {
                    Text(
                        text = stringResource(Res.string.msg_already_have_account),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = CarbonGray,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun RegisterPreview() {
    SquadifyTheme { RegisterContent(RegisterUiState.Idle(), {}, { _, _, _, _, _, _ -> }) }
}

@Preview(widthDp = 900, heightDp = 480)
@Composable
private fun RegisterLandscapePreview() {
    SquadifyTheme { RegisterContent(RegisterUiState.Idle(), {}, { _, _, _, _, _, _ -> }) }
}

@Preview
@Composable
private fun RegisterErrorPreview() {
    SquadifyTheme { RegisterContent(RegisterUiState.Idle(error = "An account with this email already exists"), {}, { _, _, _, _, _, _ -> }) }
}
