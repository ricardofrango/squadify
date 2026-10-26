package com.ricardofrango.squadify.presentation.ui.editplayer

import com.ricardofrango.squadify.presentation.ui.theme.ScreenBackground
import com.ricardofrango.squadify.presentation.ui.components.SectionCard
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import com.ricardofrango.squadify.presentation.ui.theme.ClubChrome
import com.ricardofrango.squadify.presentation.ui.theme.ChromeAccent
import androidx.compose.foundation.text.KeyboardActions
import com.ricardofrango.squadify.presentation.ui.components.submitKeyboardActions
import com.ricardofrango.squadify.presentation.navigation.collectSettledState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.model.PlayerPosition
import com.ricardofrango.squadify.presentation.ui.components.DatePickerSheet
import com.ricardofrango.squadify.presentation.ui.components.SquadifyTopBar
import com.ricardofrango.squadify.presentation.ui.components.PersonPhoto
import com.ricardofrango.squadify.presentation.ui.components.PhotoActions
import com.ricardofrango.squadify.presentation.ui.components.PrimaryLoadingButton
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.ScreenPadding
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyClubTheme
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.screenContentPadding
import com.ricardofrango.squadify.presentation.ui.components.labelRes
import squadify.app.shared.generated.resources.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock

private val FormRoundedShape = RoundedCornerShape(18.dp)

private val DateDisplayFormat = LocalDate.Format {
    day(); char(' '); monthName(MonthNames.ENGLISH_ABBREVIATED); char(' '); year()
}

/**
 * Edit an existing player's record -- name, date of birth, and the optional fields
 * (`UpdatePlayerRequest`'s `position`/`heightCm`/`weightKg`/`emergencyContact`) that
 * `CreatePlayersScreen` deliberately leaves out of the quick-add batch flow. Built to design canvas
 * artboards "17A" (filled) / "17B" (optional fields never set) -- same field style as Create Players,
 * plus a header checkmark that mirrors the pinned Save button and stays disabled until the form is
 * both valid and actually different from what was loaded.
 */
@Composable
fun EditPlayerScreen(
    playerId: String,
    onNavigateBack: () -> Unit,
    onPlayerUpdated: () -> Unit,
    onPhotoChanged: () -> Unit = {},
    viewModel: EditPlayerViewModel = koinViewModel()
) {
    SquadifyClubTheme {
        val uiState = viewModel.state.collectSettledState()
        val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
        var datePickerOpen by remember { mutableStateOf(false) }

        LaunchedEffect(playerId) { viewModel.load(playerId) }
        LaunchedEffect(viewModel.navigationEvent) {
            viewModel.navigationEvent.collect { event ->
                when (event) {
                    EditPlayerNavigationEvent.NavigateBackWithPlayerUpdated -> onPlayerUpdated()
                }
            }
        }

        EditPlayerContent(
            uiState = uiState,
            onNavigateBack = onNavigateBack,
            onRetry = { viewModel.load(playerId) },
            onFirstNameChange = { value -> viewModel.updateForm { it.copy(firstName = value) } },
            onLastNameChange = { value -> viewModel.updateForm { it.copy(lastName = value) } },
            onDateClick = { datePickerOpen = true },
            onShirtNumberChange = { value -> viewModel.updateForm { it.copy(shirtNumber = value) } },
            onPositionSelected = { value -> viewModel.updateForm { it.copy(position = value) } },
            onHeightChange = { value -> viewModel.updateForm { it.copy(heightCm = value) } },
            onWeightChange = { value -> viewModel.updateForm { it.copy(weightKg = value) } },
            onEmergencyContactChange = { value -> viewModel.updateForm { it.copy(emergencyContact = value) } },
            onSaveClick = { viewModel.onSaveClicked(playerId) },
            onPhotoPicked = { bytes -> viewModel.onPhotoPicked(playerId, bytes, onPhotoChanged) },
            onRemovePhoto = { viewModel.onRemovePhoto(playerId, onPhotoChanged) }
        )

        val idle = uiState as? EditPlayerUiState.Idle
        if (datePickerOpen && idle != null) {
            DatePickerSheet(
                title = Res.string.label_select_birth_date,
                initialDate = idle.form.birthDateIso?.let(LocalDate::parse) ?: LocalDate(today.year - 10, 1, 1),
                maxDate = today,
                onConfirm = { picked ->
                    viewModel.updateForm { it.copy(birthDateIso = picked.toString()) }
                    datePickerOpen = false
                },
                onDismiss = { datePickerOpen = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditPlayerContent(
    uiState: EditPlayerUiState,
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onDateClick: () -> Unit,
    onShirtNumberChange: (Int?) -> Unit,
    onPositionSelected: (PlayerPosition) -> Unit,
    onHeightChange: (Int?) -> Unit,
    onWeightChange: (Int?) -> Unit,
    onEmergencyContactChange: (String) -> Unit,
    onSaveClick: () -> Unit,
    onPhotoPicked: (ByteArray) -> Unit = {},
    onRemovePhoto: () -> Unit = {}
) {
    val idle = uiState as? EditPlayerUiState.Idle

    Scaffold(
        containerColor = ScreenBackground,
        topBar = {
            Column(Modifier.background(ClubChrome)) {
                SquadifyTopBar(
                    title = stringResource(Res.string.title_edit_player),
                    subtitle = idle?.let { subtitleLine(it) },
                    onNavigateBack = onNavigateBack,
                    actions = {
                        IconButton(onClick = onSaveClick, enabled = idle?.canSave == true) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_check),
                                contentDescription = stringResource(Res.string.btn_save_changes),
                                tint = if (idle?.canSave == true) ChromeAccent else Color.White.copy(alpha = 0.3f)
                            )
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        val boxModifier = Modifier.fillMaxSize().padding(paddingValues).background(ScreenBackground)
        when (uiState) {
            is EditPlayerUiState.Loading -> {
                Box(boxModifier, contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PitchGreen)
                }
            }
            is EditPlayerUiState.Error -> {
                Column(boxModifier.screenContentPadding(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(uiState.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRetry) { Text(stringResource(Res.string.btn_retry)) }
                }
            }
            is EditPlayerUiState.Idle -> {
                EditPlayerForm(
                    state = uiState,
                    modifier = boxModifier,
                    onFirstNameChange = onFirstNameChange,
                    onLastNameChange = onLastNameChange,
                    onDateClick = onDateClick,
                    onShirtNumberChange = onShirtNumberChange,
                    onPositionSelected = onPositionSelected,
                    onHeightChange = onHeightChange,
                    onWeightChange = onWeightChange,
                    onEmergencyContactChange = onEmergencyContactChange,
                    onSaveClick = onSaveClick,
                    onPhotoPicked = onPhotoPicked,
                    onRemovePhoto = onRemovePhoto
                )
            }
        }
    }
}

/** "Sub-15 A · 26/27" (either half omitted if not known). */
private fun subtitleLine(state: EditPlayerUiState.Idle): String? =
    listOfNotNull(state.ageGroupLabel, state.seasonLabel).joinToString(" · ").takeIf { it.isNotBlank() }

@Composable
private fun EditPlayerForm(
    state: EditPlayerUiState.Idle,
    modifier: Modifier,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onDateClick: () -> Unit,
    onShirtNumberChange: (Int?) -> Unit,
    onPositionSelected: (PlayerPosition) -> Unit,
    onHeightChange: (Int?) -> Unit,
    onWeightChange: (Int?) -> Unit,
    onEmergencyContactChange: (String) -> Unit,
    onSaveClick: () -> Unit,
    onPhotoPicked: (ByteArray) -> Unit,
    onRemovePhoto: () -> Unit
) {
    val form = state.form

    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .screenContentPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PlayerPhotoHeader(state, onPhotoPicked, onRemovePhoto)

            if (state.optionalFieldsNeverSet) {
                OptionalFieldsBanner()
            }

            SectionCard(spacing = 16.dp) {
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

                DateField(
                    label = stringResource(Res.string.label_date_of_birth),
                    value = form.birthDateIso?.let { LocalDate.parse(it).format(DateDisplayFormat) },
                    onClick = onDateClick
                )

                FormField(
                    value = form.shirtNumber?.toString() ?: "",
                    onValueChange = { onShirtNumberChange(it.filter(Char::isDigit).toIntOrNull()) },
                    label = stringResource(Res.string.label_shirt_number_field),
                    placeholder = stringResource(Res.string.label_optional_field_placeholder),
                    keyboardType = KeyboardType.Number
                )

                PositionField(
                    selected = form.position,
                    onOptionSelected = onPositionSelected
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FormField(
                        value = form.heightCm?.toString() ?: "",
                        onValueChange = { onHeightChange(it.filter(Char::isDigit).toIntOrNull()) },
                        label = stringResource(Res.string.label_height),
                        placeholder = stringResource(Res.string.label_optional_field_placeholder),
                        keyboardType = KeyboardType.Number,
                        suffix = "cm",
                        modifier = Modifier.weight(1f)
                    )
                    FormField(
                        value = form.weightKg?.toString() ?: "",
                        onValueChange = { onWeightChange(it.filter(Char::isDigit).toIntOrNull()) },
                        label = stringResource(Res.string.label_weight),
                        placeholder = stringResource(Res.string.label_optional_field_placeholder),
                        keyboardType = KeyboardType.Number,
                        suffix = "kg",
                        modifier = Modifier.weight(1f)
                    )
                }

                Column {
                    FormField(
                        value = form.emergencyContact ?: "",
                        onValueChange = onEmergencyContactChange,
                        label = stringResource(Res.string.label_emergency_contact_field),
                        placeholder = stringResource(Res.string.label_emergency_contact_placeholder),
                        imeAction = ImeAction.Done,
                        onSubmit = { if (state.canSave) onSaveClick() }
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(Res.string.msg_emergency_contact_helper),
                        style = MaterialTheme.typography.bodySmall,
                        color = DeepCarbon.copy(alpha = 0.55f)
                    )
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding.Horizontal, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.submitError != null) {
                Text(
                    text = state.submitError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            PrimaryLoadingButton(
                text = stringResource(Res.string.btn_save_changes),
                submittingText = stringResource(Res.string.btn_saving_changes),
                isLoading = state.isSubmitting,
                enabled = state.canSave,
                onClick = onSaveClick
            )
            if (!state.isDirty) {
                Text(
                    text = stringResource(Res.string.msg_nothing_changed_yet),
                    style = MaterialTheme.typography.bodySmall,
                    color = DeepCarbon.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** The player's photo (or initials) with "Add photo" / "Change photo" and "Remove", saved straight away. */
@Composable
private fun PlayerPhotoHeader(state: EditPlayerUiState.Idle, onPhotoPicked: (ByteArray) -> Unit, onRemovePhoto: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center) {
            PersonPhoto(
                photoUrl = state.photo.url,
                initials = listOf(state.original.firstName, state.original.lastName).mapNotNull { it.trim().firstOrNull()?.uppercaseChar() }.joinToString(""),
                size = 56.dp,
                background = PitchGreen.copy(alpha = 0.15f),
                initialsColor = PitchGreen
            )
            if (state.photo.isUpdating) CircularProgressIndicator(Modifier.size(56.dp), color = PitchGreen, strokeWidth = 2.dp)
        }
        Spacer(Modifier.width(16.dp))
        PhotoActions(state.photo, onPicked = onPhotoPicked, onRemove = onRemovePhoto)
    }
}

@Composable
private fun OptionalFieldsBanner() {
    Surface(shape = RoundedCornerShape(12.dp), color = PitchGreen.copy(alpha = 0.12f)) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier.size(18.dp).background(PitchGreen, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("i", color = Color.White, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(Res.string.msg_edit_player_optional_banner),
                style = MaterialTheme.typography.bodySmall,
                color = PitchGreen
            )
        }
    }
}

@Composable
private fun DateField(label: String, value: String?, onClick: () -> Unit) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = DeepCarbon.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(4.dp))
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = FormRoundedShape,
            color = Color.White,
            border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = value ?: stringResource(Res.string.label_pick_a_date),
                    color = if (value != null) DeepCarbon else DeepCarbon.copy(alpha = 0.4f),
                    fontWeight = if (value != null) FontWeight.Bold else FontWeight.Normal
                )
                Text("›", color = DeepCarbon.copy(alpha = 0.4f), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PositionField(selected: PlayerPosition?, onOptionSelected: (PlayerPosition) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(
            text = stringResource(Res.string.label_position_field),
            style = MaterialTheme.typography.labelSmall,
            color = DeepCarbon.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box {
            Surface(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = FormRoundedShape,
                color = Color.White,
                border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = selected?.let { stringResource(it.labelRes()) } ?: stringResource(Res.string.label_position_not_set),
                        color = if (selected != null) DeepCarbon else DeepCarbon.copy(alpha = 0.4f),
                        fontWeight = if (selected != null) FontWeight.Bold else FontWeight.Normal
                    )
                    Text("▾", color = DeepCarbon.copy(alpha = 0.4f), fontWeight = FontWeight.Bold)
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(Color.White)) {
                PlayerPosition.entries.forEach { position ->
                    DropdownMenuItem(
                        text = { Text(stringResource(position.labelRes()), color = DeepCarbon) },
                        onClick = { onOptionSelected(position); expanded = false }
                    )
                }
            }
        }
    }
}


@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    suffix: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    // Set on the last field: its keyboard key saves, like the button.
    onSubmit: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        trailingIcon = suffix?.let { { Text(it, color = DeepCarbon.copy(alpha = 0.5f)) } },
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

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

private fun sampleFields(filled: Boolean) = PlayerFormFields(
    firstName = if (filled) "Tomás" else "Xavier",
    lastName = if (filled) "Bragança" else "Boaventura",
    birthDateIso = if (filled) "2011-03-12" else "2011-08-04",
    shirtNumber = if (filled) 10 else null,
    position = if (filled) PlayerPosition.MIDFIELD else null,
    heightCm = if (filled) 162 else null,
    weightKg = if (filled) 51 else null,
    emergencyContact = if (filled) "Marta Bragança · 912 345 678" else null
)

@Preview
@Composable
private fun EditPlayerFilledPreview() {
    SquadifyTheme {
        val fields = sampleFields(filled = true)
        EditPlayerContent(
            uiState = EditPlayerUiState.Idle(
                ageGroupId = "ag1",
                ageGroupLabel = "Sub-15 A",
                seasonLabel = "26/27",
                original = fields,
                form = fields.copy(lastName = "Fonseca")
            ),
            onNavigateBack = {},
            onRetry = {},
            onFirstNameChange = {},
            onLastNameChange = {},
            onDateClick = {},
            onShirtNumberChange = {},
            onPositionSelected = {},
            onHeightChange = {},
            onWeightChange = {},
            onEmergencyContactChange = {},
            onSaveClick = {}
        )
    }
}

@Preview
@Composable
private fun EditPlayerOptionalFieldsNeverSetPreview() {
    SquadifyTheme {
        val fields = sampleFields(filled = false)
        EditPlayerContent(
            uiState = EditPlayerUiState.Idle(
                ageGroupId = "ag1",
                ageGroupLabel = "Sub-15 A",
                seasonLabel = "26/27",
                original = fields,
                form = fields
            ),
            onNavigateBack = {},
            onRetry = {},
            onFirstNameChange = {},
            onLastNameChange = {},
            onDateClick = {},
            onShirtNumberChange = {},
            onPositionSelected = {},
            onHeightChange = {},
            onWeightChange = {},
            onEmergencyContactChange = {},
            onSaveClick = {}
        )
    }
}

@Preview
@Composable
private fun EditPlayerLoadingPreview() {
    SquadifyTheme {
        EditPlayerContent(
            uiState = EditPlayerUiState.Loading,
            onNavigateBack = {},
            onRetry = {},
            onFirstNameChange = {},
            onLastNameChange = {},
            onDateClick = {},
            onShirtNumberChange = {},
            onPositionSelected = {},
            onHeightChange = {},
            onWeightChange = {},
            onEmergencyContactChange = {},
            onSaveClick = {}
        )
    }
}

@Preview
@Composable
private fun EditPlayerErrorPreview() {
    SquadifyTheme {
        EditPlayerContent(
            uiState = EditPlayerUiState.Error("Couldn't load this player. Please try again."),
            onNavigateBack = {},
            onRetry = {},
            onFirstNameChange = {},
            onLastNameChange = {},
            onDateClick = {},
            onShirtNumberChange = {},
            onPositionSelected = {},
            onHeightChange = {},
            onWeightChange = {},
            onEmergencyContactChange = {},
            onSaveClick = {}
        )
    }
}
