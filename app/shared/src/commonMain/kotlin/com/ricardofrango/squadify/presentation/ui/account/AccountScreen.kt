package com.ricardofrango.squadify.presentation.ui.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.model.UserStatus
import com.ricardofrango.squadify.presentation.ui.components.FMTopBar
import com.ricardofrango.squadify.presentation.ui.components.PasswordVisibilityToggle
import com.ricardofrango.squadify.presentation.ui.components.PrimaryLoadingButton
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import squadify.app.shared.generated.resources.*

private val FormRoundedShape = RoundedCornerShape(18.dp)

private val MonthYearFormat = LocalDate.Format {
    monthName(MonthNames.ENGLISH_ABBREVIATED); char(' '); year()
}

/**
 * Self-service profile editing + change password, built to match the two account-screen mockups: a
 * dirty-tracked PROFILE section (checkmark save in the top bar, same pattern as EditPlayerScreen) and
 * an independent CHANGE PASSWORD card that submits on its own. The mock's plain-text "Show"/"Hide"
 * links are swapped for [PasswordVisibilityToggle], the icon this app already uses everywhere else a
 * password field appears (Login, Register) -- and the mock's "Reset demo" affordance, a prototype-only
 * convenience, is left out entirely.
 */
@Composable
fun AccountScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: AccountViewModel = koinViewModel()
) {
    val uiState by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                AccountNavigationEvent.NavigateToLogin -> onNavigateToLogin()
            }
        }
    }

    fun currentForm() = (viewModel.state.value as? AccountUiState.Idle)?.form

    AccountContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onRetry = { viewModel.load() },
        onFirstNameChange = { value -> currentForm()?.let { viewModel.updateProfileForm(it.copy(firstName = value)) } },
        onLastNameChange = { value -> currentForm()?.let { viewModel.updateProfileForm(it.copy(lastName = value)) } },
        onPhoneChange = { value -> currentForm()?.let { viewModel.updateProfileForm(it.copy(phone = value)) } },
        onSaveProfileClick = { viewModel.onSaveProfileClicked() },
        onPasswordFormChange = { form -> viewModel.updatePasswordForm(form) },
        onChangePasswordClick = { viewModel.onChangePasswordClicked() },
        onLogoutClick = { viewModel.onLogoutClicked() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountContent(
    uiState: AccountUiState,
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onSaveProfileClick: () -> Unit,
    onPasswordFormChange: (PasswordFormFields) -> Unit,
    onChangePasswordClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    val idle = uiState as? AccountUiState.Idle

    Scaffold(
        containerColor = Color.White,
        topBar = {
            Column(Modifier.background(CarbonGray)) {
                FMTopBar(
                    title = stringResource(Res.string.title_account),
                    subtitle = idle?.let {
                        stringResource(
                            Res.string.msg_member_since_status,
                            it.memberSince.format(MonthYearFormat),
                            statusLabel(it.status)
                        )
                    },
                    onNavigateBack = onNavigateBack,
                    actions = {
                        IconButton(onClick = onSaveProfileClick, enabled = idle?.canSaveProfile == true) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_check),
                                contentDescription = stringResource(Res.string.btn_save_changes),
                                tint = if (idle?.canSaveProfile == true) PitchGreen else Color.White.copy(alpha = 0.3f)
                            )
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        val boxModifier = Modifier.fillMaxSize().padding(paddingValues).background(Color.White)
        when (uiState) {
            is AccountUiState.Loading -> {
                Box(boxModifier, contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PitchGreen)
                }
            }
            is AccountUiState.Error -> {
                Column(boxModifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(uiState.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRetry) { Text(stringResource(Res.string.btn_retry)) }
                }
            }
            is AccountUiState.Idle -> {
                AccountForm(
                    state = uiState,
                    modifier = boxModifier,
                    onFirstNameChange = onFirstNameChange,
                    onLastNameChange = onLastNameChange,
                    onPhoneChange = onPhoneChange,
                    onPasswordFormChange = onPasswordFormChange,
                    onChangePasswordClick = onChangePasswordClick,
                    onLogoutClick = onLogoutClick
                )
            }
        }
    }
}

@Composable
private fun statusLabel(status: UserStatus) = stringResource(
    when (status) {
        UserStatus.ACTIVE -> Res.string.label_active
        UserStatus.INACTIVE -> Res.string.label_inactive
        UserStatus.PENDING -> Res.string.label_pending_status
        UserStatus.SUSPENDED -> Res.string.label_suspended_status
    }
)

@Composable
private fun AccountForm(
    state: AccountUiState.Idle,
    modifier: Modifier,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onPasswordFormChange: (PasswordFormFields) -> Unit,
    onChangePasswordClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    val form = state.form
    val passwordForm = state.passwordForm

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        ProfileHeader(state)

        FormField(
            value = form.firstName,
            onValueChange = onFirstNameChange,
            label = stringResource(Res.string.label_first_name_field)
        )
        FormField(
            value = form.lastName,
            onValueChange = onLastNameChange,
            label = stringResource(Res.string.label_last_name_field)
        )
        FormField(
            value = form.phone,
            onValueChange = onPhoneChange,
            label = stringResource(Res.string.label_phone_field),
            keyboardType = KeyboardType.Phone
        )

        ReadOnlyEmailField(email = state.email)

        if (state.profileSubmitError != null) {
            Text(
                text = state.profileSubmitError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        HorizontalDivider(color = CarbonGray.copy(alpha = 0.15f))

        ChangePasswordCard(
            form = passwordForm,
            isSubmitting = state.isSubmittingPassword,
            submitError = state.passwordSubmitError,
            passwordChanged = state.passwordChanged,
            canSubmit = state.canChangePassword,
            onFormChange = onPasswordFormChange,
            onSubmit = onChangePasswordClick
        )

        OutlinedButton(
            onClick = onLogoutClick,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Text(
                text = stringResource(Res.string.btn_logout),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
private fun ProfileHeader(state: AccountUiState.Idle) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(56.dp).background(PitchGreen.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = state.initials,
                color = PitchGreen,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                text = stringResource(Res.string.label_profile_section),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = DeepCarbon.copy(alpha = 0.6f)
            )
            Text(
                text = stringResource(
                    if (state.isDirty) Res.string.msg_unsaved_profile_changes else Res.string.msg_no_profile_changes
                ),
                style = MaterialTheme.typography.bodySmall,
                color = DeepCarbon.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun ReadOnlyEmailField(email: String) {
    Column {
        Text(
            text = stringResource(Res.string.label_email),
            style = MaterialTheme.typography.labelSmall,
            color = DeepCarbon.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(4.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = FormRoundedShape,
            color = CarbonGray.copy(alpha = 0.06f),
            border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.2f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(email, color = DeepCarbon, fontWeight = FontWeight.Bold)
                Surface(shape = RoundedCornerShape(50), color = PitchGreen.copy(alpha = 0.15f)) {
                    Text(
                        text = stringResource(Res.string.label_verified_badge),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = PitchGreen,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(Res.string.msg_used_to_sign_in),
                style = MaterialTheme.typography.bodySmall,
                color = DeepCarbon.copy(alpha = 0.5f)
            )
            /*
            Text(
                text = stringResource(Res.string.btn_change_email),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = PitchGreen,
            )*/
        }
    }
}

@Composable
private fun ChangePasswordCard(
    form: PasswordFormFields,
    isSubmitting: Boolean,
    submitError: String?,
    passwordChanged: Boolean,
    canSubmit: Boolean,
    onFormChange: (PasswordFormFields) -> Unit,
    onSubmit: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(Res.string.label_change_password_section),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = DeepCarbon.copy(alpha = 0.6f)
            )

            PasswordField(
                value = form.currentPassword,
                onValueChange = { onFormChange(form.copy(currentPassword = it)) },
                label = stringResource(Res.string.label_current_password_field),
                visible = form.currentVisible,
                onVisibilityToggle = { onFormChange(form.copy(currentVisible = !form.currentVisible)) }
            )

            Column {
                PasswordField(
                    value = form.newPassword,
                    onValueChange = { onFormChange(form.copy(newPassword = it)) },
                    label = stringResource(Res.string.label_new_password_field),
                    visible = form.newVisible,
                    onVisibilityToggle = { onFormChange(form.copy(newVisible = !form.newVisible)) },
                    isError = form.newPasswordTooShort
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.msg_password_min_length),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (form.newPasswordTooShort) MaterialTheme.colorScheme.error else DeepCarbon.copy(alpha = 0.5f)
                )
            }

            Column {
                PasswordField(
                    value = form.confirmPassword,
                    onValueChange = { onFormChange(form.copy(confirmPassword = it)) },
                    label = stringResource(Res.string.label_confirm_new_password_field),
                    visible = form.confirmVisible,
                    onVisibilityToggle = { onFormChange(form.copy(confirmVisible = !form.confirmVisible)) },
                    isError = form.confirmMismatch
                )
                if (form.confirmMismatch) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(Res.string.msg_passwords_do_not_match),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (submitError != null) {
                Text(
                    text = submitError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (passwordChanged) {
                Text(
                    text = stringResource(Res.string.msg_password_changed),
                    style = MaterialTheme.typography.bodySmall,
                    color = PitchGreen
                )
            }

            PrimaryLoadingButton(
                text = stringResource(Res.string.btn_change_password),
                submittingText = stringResource(Res.string.btn_changing_password),
                isLoading = isSubmitting,
                enabled = canSubmit,
                onClick = onSubmit
            )
        }
    }
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        shape = FormRoundedShape,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = DeepCarbon,
            unfocusedTextColor = DeepCarbon,
            focusedBorderColor = PitchGreen,
            unfocusedBorderColor = CarbonGray.copy(alpha = 0.3f),
            focusedLabelColor = PitchGreen,
            unfocusedLabelColor = CarbonGray
        )
    )
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onVisibilityToggle: () -> Unit,
    isError: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = { PasswordVisibilityToggle(visible = visible, onToggle = onVisibilityToggle, tint = CarbonGray) },
        modifier = Modifier.fillMaxWidth(),
        shape = FormRoundedShape,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = DeepCarbon,
            unfocusedTextColor = DeepCarbon,
            focusedBorderColor = PitchGreen,
            unfocusedBorderColor = CarbonGray.copy(alpha = 0.3f),
            focusedLabelColor = PitchGreen,
            unfocusedLabelColor = CarbonGray
        )
    )
}

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

private fun sampleFields(dirty: Boolean) = ProfileFormFields(
    firstName = "Ricardo",
    lastName = if (dirty) "Frango Jr." else "Frango",
    phone = "+351 912 345 678"
)

private fun sampleIdle(dirty: Boolean = false) = AccountUiState.Idle(
    email = "ricardo@cdribamar.pt",
    memberSince = LocalDate(2026, 8, 1),
    status = UserStatus.ACTIVE,
    original = sampleFields(dirty = false),
    form = sampleFields(dirty = dirty)
)

@Preview
@Composable
private fun AccountScreenPreview() {
    SquadifyTheme {
        AccountContent(
            uiState = sampleIdle(),
            onNavigateBack = {},
            onRetry = {},
            onFirstNameChange = {},
            onLastNameChange = {},
            onPhoneChange = {},
            onSaveProfileClick = {},
            onPasswordFormChange = {},
            onChangePasswordClick = {},
            onLogoutClick = {}
        )
    }
}

@Preview
@Composable
private fun AccountScreenDirtyPreview() {
    SquadifyTheme {
        AccountContent(
            uiState = sampleIdle(dirty = true),
            onNavigateBack = {},
            onRetry = {},
            onFirstNameChange = {},
            onLastNameChange = {},
            onPhoneChange = {},
            onSaveProfileClick = {},
            onPasswordFormChange = {},
            onChangePasswordClick = {},
            onLogoutClick = {}
        )
    }
}

@Preview
@Composable
private fun AccountScreenLoadingPreview() {
    SquadifyTheme {
        AccountContent(
            uiState = AccountUiState.Loading,
            onNavigateBack = {},
            onRetry = {},
            onFirstNameChange = {},
            onLastNameChange = {},
            onPhoneChange = {},
            onSaveProfileClick = {},
            onPasswordFormChange = {},
            onChangePasswordClick = {},
            onLogoutClick = {}
        )
    }
}

@Preview
@Composable
private fun AccountScreenErrorPreview() {
    SquadifyTheme {
        AccountContent(
            uiState = AccountUiState.Error("Couldn't load your account. Please try again."),
            onNavigateBack = {},
            onRetry = {},
            onFirstNameChange = {},
            onLastNameChange = {},
            onPhoneChange = {},
            onSaveProfileClick = {},
            onPasswordFormChange = {},
            onChangePasswordClick = {},
            onLogoutClick = {}
        )
    }
}
