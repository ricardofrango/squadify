package com.ricardofrango.squadify.presentation.ui.account

import com.ricardofrango.squadify.presentation.ui.components.safeSidesPadding
import com.ricardofrango.squadify.presentation.ui.components.ScreenWindowInsets
import com.ricardofrango.squadify.presentation.ui.components.ErrorScreen
import com.ricardofrango.squadify.presentation.ui.components.SectionCard
import com.ricardofrango.squadify.presentation.ui.theme.ScreenBackground
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.ricardofrango.squadify.presentation.credentials.rememberCredentialStore
import com.ricardofrango.squadify.presentation.ui.theme.ClubChrome
import com.ricardofrango.squadify.presentation.ui.theme.ChromeAccent
import com.ricardofrango.squadify.presentation.ui.components.SignedOutAlert
import androidx.compose.foundation.clickable
import com.ricardofrango.squadify.presentation.ui.components.submitKeyboardActions
import androidx.compose.foundation.text.KeyboardActions
import com.ricardofrango.squadify.presentation.navigation.collectSettledState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.ricardofrango.squadify.presentation.ui.components.readableWidth
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
import com.ricardofrango.squadify.presentation.ui.components.SquadifyTopBar
import com.ricardofrango.squadify.presentation.ui.components.PersonPhoto
import com.ricardofrango.squadify.presentation.ui.components.PhotoActions
import com.ricardofrango.squadify.presentation.ui.components.PasswordVisibilityToggle
import com.ricardofrango.squadify.presentation.ui.components.PrimaryLoadingButton
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyClubTheme
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import com.ricardofrango.squadify.presentation.ui.theme.screenContentPadding
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
    onNavigateToChangeEmail: (currentEmail: String) -> Unit,
    // Set when coming back from Change email with the new address.
    changedEmail: String? = null,
    // Delete account, where the app has it (AppFeatures.accountDeletion).
    onNavigateToDeleteAccount: () -> Unit = {},
    viewModel: AccountViewModel = koinViewModel()
) {
    SquadifyClubTheme {
        val uiState = viewModel.state.collectSettledState()

        LaunchedEffect(Unit) {
            viewModel.navigationEvent.collect { event ->
                when (event) {
                    AccountNavigationEvent.NavigateToLogin -> onNavigateToLogin()
                }
            }
        }

        LaunchedEffect(changedEmail, uiState is AccountUiState.Idle) {
            if (changedEmail != null) viewModel.onEmailChanged(changedEmail)
        }

        if ((uiState as? AccountUiState.Idle)?.signedOut == true) {
            SignedOutAlert(onConfirm = { viewModel.onLogoutClicked() })
        }

        // After a password change, the new password is offered to the password manager (Android's
        // Credential Manager; nothing elsewhere). It's taken from the form as it's sent, since the
        // form is cleared on success.
        val credentialStore = rememberCredentialStore()
        var sentPassword by remember { mutableStateOf<String?>(null) }
        val passwordChanged = (uiState as? AccountUiState.Idle)?.passwordChanged == true
        LaunchedEffect(passwordChanged) {
            val email = (uiState as? AccountUiState.Idle)?.email
            val password = sentPassword
            if (passwordChanged && email != null && password != null) {
                sentPassword = null
                credentialStore.save(email, password)
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
            onChangePasswordClick = {
                sentPassword = (viewModel.state.value as? AccountUiState.Idle)?.passwordForm?.newPassword
                viewModel.onChangePasswordClicked()
            },
            onChangeEmailClick = onNavigateToChangeEmail,
            onLogoutClick = { viewModel.onLogoutClicked() },
            onPhotoPicked = viewModel::onPhotoPicked,
            onRemovePhoto = viewModel::onRemovePhoto,
            showPhoto = viewModel.showsPhoto,
            onDeleteAccountClick = if (viewModel.offersAccountDeletion) onNavigateToDeleteAccount else null
        )
    }
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
    onChangeEmailClick: (String) -> Unit = {},
    onLogoutClick: () -> Unit,
    onPhotoPicked: (ByteArray) -> Unit = {},
    onRemovePhoto: () -> Unit = {},
    // False hides the photo: initials only, no photo actions (AppFeatures.photos).
    showPhoto: Boolean = true,
    // Null: no "Delete account" (the clubs app, for now).
    onDeleteAccountClick: (() -> Unit)? = null
) {
    val idle = uiState as? AccountUiState.Idle

    Scaffold(
        contentWindowInsets = ScreenWindowInsets,
        containerColor = ScreenBackground,
        topBar = {
            Column(Modifier.background(ClubChrome).safeSidesPadding()) {
                SquadifyTopBar(
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
                                tint = if (idle?.canSaveProfile == true) ChromeAccent else Color.White.copy(alpha = 0.3f)
                            )
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        val boxModifier = Modifier.fillMaxSize().padding(paddingValues).background(ScreenBackground)
        when (uiState) {
            is AccountUiState.Loading -> {
                Box(boxModifier, contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PitchGreen)
                }
            }
            is AccountUiState.Error -> {
                ErrorScreen(title = uiState.message, body = null, onRetry = onRetry, modifier = boxModifier)
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
                    onChangeEmailClick = onChangeEmailClick,
                    onSaveProfileClick = onSaveProfileClick,
                    onLogoutClick = onLogoutClick,
                    onPhotoPicked = onPhotoPicked,
                    onRemovePhoto = onRemovePhoto,
                    showPhoto = showPhoto,
                    onDeleteAccountClick = onDeleteAccountClick
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
    onChangeEmailClick: (String) -> Unit,
    onSaveProfileClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onPhotoPicked: (ByteArray) -> Unit,
    onRemovePhoto: () -> Unit,
    showPhoto: Boolean,
    onDeleteAccountClick: (() -> Unit)?
) {
    val form = state.form
    val passwordForm = state.passwordForm

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .readableWidth()
            .screenContentPadding(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        SectionCard(spacing = 20.dp) {
            ProfileHeader(state, onPhotoPicked, onRemovePhoto, showPhoto)

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
                keyboardType = KeyboardType.Phone,
                // Last profile field: the keyboard key saves, like the top bar's check (when it's enabled).
                imeAction = ImeAction.Done,
                onSubmit = { if (state.canSaveProfile) onSaveProfileClick() }
            )

            ReadOnlyEmailField(email = state.email, changed = state.emailChanged, onChangeClick = { onChangeEmailClick(state.email) })
        }
        if (state.profileSubmitError != null) {
            Text(
                text = state.profileSubmitError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

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

        if (onDeleteAccountClick != null) {
            TextButton(onClick = onDeleteAccountClick, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.btn_delete_account), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ProfileHeader(state: AccountUiState.Idle, onPhotoPicked: (ByteArray) -> Unit, onRemovePhoto: () -> Unit, showPhoto: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center) {
            PersonPhoto(
                photoUrl = state.photo.url.takeIf { showPhoto },
                initials = state.initials,
                size = 56.dp,
                background = PitchGreen.copy(alpha = 0.15f),
                initialsColor = PitchGreen
            )
            if (state.photo.isUpdating) CircularProgressIndicator(Modifier.size(56.dp), color = PitchGreen, strokeWidth = 2.dp)
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                text = stringResource(Res.string.label_profile_section),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = DeepCarbon.copy(alpha = 0.6f)
            )
            if (state.isDirty) {
                Text(
                    text = stringResource(Res.string.msg_unsaved_profile_changes),
                    style = MaterialTheme.typography.bodySmall,
                    color = DeepCarbon.copy(alpha = 0.5f)
                )
            }
            if (showPhoto) PhotoActions(state.photo, onPicked = onPhotoPicked, onRemove = onRemovePhoto)
        }
    }
}

@Composable
private fun ReadOnlyEmailField(email: String, changed: Boolean, onChangeClick: () -> Unit) {
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
            Text(
                text = stringResource(Res.string.btn_change_email),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = PitchGreen,
                modifier = Modifier.clickable(onClick = onChangeClick)
            )
        }
        if (changed) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.msg_email_changed),
                style = MaterialTheme.typography.bodySmall,
                color = PitchGreen
            )
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
                    isError = form.confirmMismatch,
                    onSubmit = { if (canSubmit && !isSubmitting) onSubmit() }
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
    imeAction: ImeAction = ImeAction.Next,
    onSubmit: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        shape = FormRoundedShape,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = onSubmit?.let { submitKeyboardActions(it) } ?: KeyboardActions.Default,
        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White, disabledContainerColor = Color.White, 
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
    isError: Boolean = false,
    // Set on the last field (confirm): its keyboard key changes the password, like the button.
    onSubmit: (() -> Unit)? = null
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
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (onSubmit != null) ImeAction.Done else ImeAction.Next),
        keyboardActions = onSubmit?.let { submitKeyboardActions(it) } ?: KeyboardActions.Default,
        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White, disabledContainerColor = Color.White, 
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

@Preview(widthDp = 900, heightDp = 480)
@Composable
private fun AccountScreenLandscapePreview() {
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

// Without photos (AppFeatures.photos off): initials, no photo actions.
@Composable
private fun AccountNoPhotoPreviewContent() {
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
            onLogoutClick = {},
            showPhoto = false,
            onDeleteAccountClick = {}
        )
    }
}

@Preview
@Composable
private fun AccountScreenNoPhotoPreview() = AccountNoPhotoPreviewContent()

@Preview(widthDp = 900, heightDp = 480)
@Composable
private fun AccountScreenNoPhotoLandscapePreview() = AccountNoPhotoPreviewContent()
