package com.ricardofrango.squadify.presentation.ui.opponents

import com.ricardofrango.squadify.presentation.ui.components.keyboardOnShortWindow
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import com.ricardofrango.squadify.presentation.ui.components.safeSidesPadding
import com.ricardofrango.squadify.presentation.ui.components.ScreenWindowInsets
import com.ricardofrango.squadify.presentation.ui.components.ErrorScreen
import com.ricardofrango.squadify.presentation.ui.components.isWide
import com.ricardofrango.squadify.presentation.ui.components.SectionCard
import com.ricardofrango.squadify.presentation.ui.theme.ScreenBackground
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.model.Opponent
import com.ricardofrango.squadify.presentation.ui.components.SquadifyTopBar
import com.ricardofrango.squadify.presentation.ui.components.FormErrorLine
import com.ricardofrango.squadify.presentation.ui.components.FormTextField
import com.ricardofrango.squadify.presentation.ui.components.PrimaryLoadingButton
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.ScreenPadding
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyClubTheme
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.btn_create_opponent
import squadify.app.shared.generated.resources.btn_creating_opponent
import squadify.app.shared.generated.resources.btn_open_in_maps
import squadify.app.shared.generated.resources.label_add_logo
import squadify.app.shared.generated.resources.label_contact_email_placeholder
import squadify.app.shared.generated.resources.label_contact_phone_field
import squadify.app.shared.generated.resources.label_contact_phone_placeholder
import squadify.app.shared.generated.resources.label_email
import squadify.app.shared.generated.resources.label_identity_section
import squadify.app.shared.generated.resources.label_opponent_contact_section
import squadify.app.shared.generated.resources.label_opponent_name_field
import squadify.app.shared.generated.resources.label_opponent_name_placeholder
import squadify.app.shared.generated.resources.label_opponent_notes_field
import squadify.app.shared.generated.resources.label_opponent_notes_placeholder
import squadify.app.shared.generated.resources.label_opponent_notes_section
import squadify.app.shared.generated.resources.label_opponent_short_name_field
import squadify.app.shared.generated.resources.label_opponent_short_name_placeholder
import squadify.app.shared.generated.resources.label_opponent_stadium_section
import squadify.app.shared.generated.resources.label_optional_placeholder
import squadify.app.shared.generated.resources.label_stadium_address_field
import squadify.app.shared.generated.resources.label_stadium_name_field
import squadify.app.shared.generated.resources.msg_opponent_logo_hint
import squadify.app.shared.generated.resources.label_change_logo
import squadify.app.shared.generated.resources.btn_remove_logo
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.readBytes
import androidx.compose.foundation.clickable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.ricardofrango.squadify.presentation.ui.theme.AlertRed
import com.ricardofrango.squadify.presentation.ui.theme.screenContentPadding
import squadify.app.shared.generated.resources.msg_opponent_name_taken
import squadify.app.shared.generated.resources.msg_opponent_short_name_helper
import squadify.app.shared.generated.resources.msg_stadium_address_helper
import squadify.app.shared.generated.resources.title_create_opponent
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import squadify.app.shared.generated.resources.btn_retry
import squadify.app.shared.generated.resources.btn_save_changes
import squadify.app.shared.generated.resources.btn_saving_changes
import squadify.app.shared.generated.resources.msg_check_connection
import squadify.app.shared.generated.resources.msg_opponent_load_failed
import squadify.app.shared.generated.resources.title_edit_opponent

/**
 * Create opponent (design 38e): logo, IDENTITY, STADIUM, CONTACT and NOTES,
 * with the button pinned at the bottom. Only Name is required; a name the club already has shows
 * inline under it and keeps the button disabled. Next moves field to field and Done on Phone submits;
 * Notes is multi-line and reached by tapping. [initialName] prefills Name (the search that found
 * nothing). [onCreated] gets the new opponent.
 */
@Composable
fun OpponentFormScreen(
    clubId: String,
    ageGroupId: String?,
    initialName: String,
    onNavigateBack: () -> Unit,
    onCreated: (Opponent) -> Unit,
    viewModel: OpponentFormViewModel = koinViewModel()
) {
    SquadifyClubTheme {
        val state by viewModel.state.collectAsStateWithLifecycle()
        LaunchedEffect(clubId) { viewModel.start(clubId, ageGroupId, initialName) }
        LaunchedEffect(viewModel) { viewModel.done.collect { onCreated(it) } }

        OpponentFormContent(
            state = state,
            title = Res.string.title_create_opponent,
            submitText = Res.string.btn_create_opponent,
            submittingText = Res.string.btn_creating_opponent,
            onNavigateBack = onNavigateBack,
            onRetry = {},
            onFormChange = viewModel::onFormChange,
            onNameFocusLost = viewModel::onNameFocusLost,
            onSubmit = viewModel::onSubmit,
            showLogo = viewModel.showsLogo,
            onLogoPicked = viewModel::onLogoPicked,
            onRemoveLogo = viewModel::onRemoveLogo
        )
    }
}

/**
 * Edit opponent (design 38e), from Opponent details: the same form, prefilled. "Save changes" is enabled
 * only once something changed; emptying an optional field clears it. [onSaved] is called after saving.
 */
@Composable
fun OpponentEditScreen(
    opponentId: String,
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: OpponentFormViewModel = koinViewModel()
) {
    SquadifyClubTheme {
        val state by viewModel.state.collectAsStateWithLifecycle()
        LaunchedEffect(opponentId) { viewModel.startEdit(opponentId) }
        LaunchedEffect(viewModel) { viewModel.done.collect { onSaved() } }

        OpponentFormContent(
            state = state,
            title = Res.string.title_edit_opponent,
            submitText = Res.string.btn_save_changes,
            submittingText = Res.string.btn_saving_changes,
            onNavigateBack = onNavigateBack,
            onRetry = { viewModel.startEdit(opponentId, force = true) },
            onFormChange = viewModel::onFormChange,
            onNameFocusLost = viewModel::onNameFocusLost,
            onSubmit = viewModel::onSubmit,
            showLogo = viewModel.showsLogo,
            onLogoPicked = viewModel::onLogoPicked,
            onRemoveLogo = viewModel::onRemoveLogo
        )
    }
}

@Composable
private fun OpponentFormContent(
    state: OpponentFormUiState,
    title: StringResource,
    submitText: StringResource,
    submittingText: StringResource,
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit,
    onFormChange: (OpponentForm) -> Unit,
    onNameFocusLost: () -> Unit,
    onSubmit: () -> Unit,
    // False without the server's image storage (the coach app, for now).
    showLogo: Boolean = true,
    onLogoPicked: (ByteArray) -> Unit = {},
    onRemoveLogo: () -> Unit = {}
) {
    Scaffold(
        // Top and bottom only: the action bar is in the content, so its white must reach the sides; the
        // content and the bar keep clear of the camera cutout themselves (safeSidesPadding).
        contentWindowInsets = ScreenWindowInsets.only(WindowInsetsSides.Vertical),
        containerColor = ScreenBackground,
        topBar = { SquadifyTopBar(title = stringResource(title), subtitle = state.clubName, onNavigateBack = onNavigateBack) }
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PitchGreen) }
            return@Scaffold
        }
        if (state.loadFailed) {
            ErrorScreen(title = stringResource(Res.string.msg_opponent_load_failed), onRetry = onRetry, modifier = Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().background(ScreenBackground)
        ) {
            // Landscape or tablet: the sections flow into two columns, the button pinned bottom-right.
            val wide = isWide
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).safeSidesPadding()) {
                    if (wide) {
                        Row(Modifier.screenContentPadding(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                if (showLogo) LogoHeader(state, onLogoPicked, onRemoveLogo)
                                IdentitySection(state, onFormChange, onNameFocusLost)
                                StadiumSection(state, onFormChange)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                ContactSection(state, onFormChange, onSubmit)
                                NotesSection(state, onFormChange)
                                FormErrorLine(state.error)
                            }
                        }
                    } else {
                        Column(Modifier.screenContentPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (showLogo) LogoHeader(state, onLogoPicked, onRemoveLogo)
                            IdentitySection(state, onFormChange, onNameFocusLost)
                            StadiumSection(state, onFormChange)
                            ContactSection(state, onFormChange, onSubmit)
                            NotesSection(state, onFormChange)
                            FormErrorLine(state.error)
                        }
                    }
                }
                if (!keyboardOnShortWindow()) Surface(color = Color.White, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.safeSidesPadding().padding(horizontal = ScreenPadding.Horizontal, vertical = 16.dp), contentAlignment = Alignment.CenterEnd) {
                        PrimaryLoadingButton(
                            text = submitText,
                            submittingText = submittingText,
                            isLoading = state.submitting,
                            enabled = state.canSubmit,
                            onClick = onSubmit,
                            modifier = if (wide) Modifier.widthIn(max = 320.dp) else Modifier
                        )
                    }
                }
            }
        }
    }
}

/**
 * The logo (or initials) with "Add logo" / "Change logo" and "Remove". The picker opens the platform's
 * gallery; the image is shown straight away and saved with the form.
 */
@Composable
private fun LogoHeader(state: OpponentFormUiState, onLogoPicked: (ByteArray) -> Unit, onRemoveLogo: () -> Unit) {
    val scope = rememberCoroutineScope()
    val picker = rememberFilePickerLauncher(type = FileKitType.Image) { file ->
        if (file != null) scope.launch { onLogoPicked(file.readBytes()) }
    }
    val enabled = !state.submitting
    Row(verticalAlignment = Alignment.CenterVertically) {
        OpponentLogo(state.form.name.ifBlank { "?" }, state.form.shortName, logoUrl = state.logo.shownUrl, size = 64.dp, picked = state.logo.picked)
        Spacer(Modifier.width(16.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(if (state.logo.hasLogo) Res.string.label_change_logo else Res.string.label_add_logo),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (enabled) PitchGreen else PitchGreen.copy(alpha = 0.4f),
                    modifier = Modifier.clickable(enabled = enabled) { picker.launch() }.padding(vertical = 4.dp)
                )
                if (state.logo.hasLogo) {
                    Text(" · ", color = DeepCarbon.copy(alpha = 0.3f))
                    Text(
                        stringResource(Res.string.btn_remove_logo),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (enabled) AlertRed else AlertRed.copy(alpha = 0.4f),
                        modifier = Modifier.clickable(enabled = enabled, onClick = onRemoveLogo).padding(vertical = 4.dp)
                    )
                }
            }
            Text(
                stringResource(Res.string.msg_opponent_logo_hint),
                style = MaterialTheme.typography.bodySmall,
                color = DeepCarbon.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun SectionLabel(text: StringResource) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = DeepCarbon.copy(alpha = 0.6f)
    )
}

@Composable
private fun Section(label: StringResource, content: @Composable ColumnScope.() -> Unit) {
    // Each section is a white card on the grey; the gap between cards separates them.
    SectionCard(spacing = 16.dp) {
        SectionLabel(label)
        content()
    }
}

@Composable
private fun ColumnScope.IdentitySection(state: OpponentFormUiState, onFormChange: (OpponentForm) -> Unit, onNameFocusLost: () -> Unit) {
    val form = state.form
    var nameFocused by remember { mutableStateOf(false) }
    Section(Res.string.label_identity_section) {
        FormTextField(
            value = form.name,
            onValueChange = { onFormChange(form.copy(name = it)) },
            label = stringResource(Res.string.label_opponent_name_field),
            placeholder = stringResource(Res.string.label_opponent_name_placeholder),
            enabled = !state.submitting,
            keyboardType = KeyboardType.Text,
            isError = state.duplicateName,
            supporting = if (state.duplicateName) stringResource(Res.string.msg_opponent_name_taken) else null,
            // Checked when the field loses focus, as well as on submit.
            modifier = Modifier.onFocusChanged {
                if (nameFocused && !it.isFocused) onNameFocusLost()
                nameFocused = it.isFocused
            }
        )
        FormTextField(
            value = form.shortName,
            onValueChange = { onFormChange(form.copy(shortName = it)) },
            label = stringResource(Res.string.label_opponent_short_name_field),
            placeholder = stringResource(Res.string.label_opponent_short_name_placeholder),
            supporting = stringResource(Res.string.msg_opponent_short_name_helper),
            enabled = !state.submitting,
            keyboardType = KeyboardType.Text
        )
    }
}

@Composable
private fun ColumnScope.StadiumSection(state: OpponentFormUiState, onFormChange: (OpponentForm) -> Unit) {
    val form = state.form
    val uriHandler = LocalUriHandler.current
    Section(Res.string.label_opponent_stadium_section) {
        FormTextField(
            value = form.stadiumName,
            onValueChange = { onFormChange(form.copy(stadiumName = it)) },
            label = stringResource(Res.string.label_stadium_name_field),
            placeholder = stringResource(Res.string.label_optional_placeholder),
            enabled = !state.submitting,
            keyboardType = KeyboardType.Text
        )
        Column {
            FormTextField(
                value = form.stadiumAddress,
                onValueChange = { onFormChange(form.copy(stadiumAddress = it)) },
                label = stringResource(Res.string.label_stadium_address_field),
                placeholder = stringResource(Res.string.label_optional_placeholder),
                supporting = stringResource(Res.string.msg_stadium_address_helper),
                enabled = !state.submitting,
                keyboardType = KeyboardType.Text
            )
            if (form.stadiumAddress.isNotBlank()) {
                TextButton(onClick = { uriHandler.openUri(mapsUrl(form.stadiumAddress)) }) {
                    Text(stringResource(Res.string.btn_open_in_maps), color = PitchGreen, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.ContactSection(state: OpponentFormUiState, onFormChange: (OpponentForm) -> Unit, onSubmit: () -> Unit) {
    val form = state.form
    Section(Res.string.label_opponent_contact_section) {
        FormTextField(
            value = form.contactEmail,
            onValueChange = { onFormChange(form.copy(contactEmail = it)) },
            label = stringResource(Res.string.label_email),
            placeholder = stringResource(Res.string.label_contact_email_placeholder),
            enabled = !state.submitting,
            keyboardType = KeyboardType.Email
        )
        // The last single-line field: Done submits, like the button.
        FormTextField(
            value = form.contactPhone,
            onValueChange = { onFormChange(form.copy(contactPhone = it)) },
            label = stringResource(Res.string.label_contact_phone_field),
            placeholder = stringResource(Res.string.label_contact_phone_placeholder),
            enabled = !state.submitting,
            keyboardType = KeyboardType.Phone,
            imeAction = ImeAction.Done,
            onImeAction = onSubmit
        )
    }
}

@Composable
private fun ColumnScope.NotesSection(state: OpponentFormUiState, onFormChange: (OpponentForm) -> Unit) {
    val form = state.form
    Section(Res.string.label_opponent_notes_section) {
        FormTextField(
            value = form.notes,
            onValueChange = { onFormChange(form.copy(notes = it)) },
            label = stringResource(Res.string.label_opponent_notes_field),
            placeholder = stringResource(Res.string.label_opponent_notes_placeholder),
            enabled = !state.submitting,
            keyboardType = KeyboardType.Text,
            singleLine = false,
            imeAction = ImeAction.Default
        )
    }
}

// ---- Previews ----

@Composable
private fun PreviewForm(state: OpponentFormUiState) {
    SquadifyTheme {
        OpponentFormContent(
            state = state,
            title = Res.string.title_create_opponent,
            submitText = Res.string.btn_create_opponent,
            submittingText = Res.string.btn_creating_opponent,
            onNavigateBack = {}, onRetry = {}, onFormChange = {}, onNameFocusLost = {}, onSubmit = {}
        )
    }
}

@Preview
@Composable
private fun CreateOpponentEmptyPreview() = PreviewForm(OpponentFormUiState(clubName = "CD Ribamar"))

@Preview
@Composable
private fun CreateOpponentDuplicatePreview() = PreviewForm(
    OpponentFormUiState(clubName = "CD Ribamar", form = OpponentForm(name = "CD Mafra"), duplicateName = true)
)

@Preview(widthDp = 900, heightDp = 420)
@Composable
private fun CreateOpponentLandscapePreview() = PreviewForm(
    OpponentFormUiState(
        clubName = "CD Ribamar",
        form = OpponentForm(name = "Sporting Lourel", stadiumAddress = "Rua do Campo 3, Mafra")
    )
)

@Preview
@Composable
private fun EditOpponentPreview() {
    val form = OpponentForm(name = "AD Fontelas", shortName = "ADF", stadiumName = "Campo das Fontelas", stadiumAddress = "Rua do Campo 3, 2640-401 Mafra")
    SquadifyTheme {
        OpponentFormContent(
            state = OpponentFormUiState(clubName = "CD Ribamar", form = form, original = form),
            title = Res.string.title_edit_opponent,
            submitText = Res.string.btn_save_changes,
            submittingText = Res.string.btn_saving_changes,
            onNavigateBack = {}, onRetry = {}, onFormChange = {}, onNameFocusLost = {}, onSubmit = {}
        )
    }
}
