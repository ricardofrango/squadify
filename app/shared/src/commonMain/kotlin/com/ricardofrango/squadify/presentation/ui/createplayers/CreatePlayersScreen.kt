package com.ricardofrango.squadify.presentation.ui.createplayers

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.model.CreatePlayer
import com.ricardofrango.squadify.presentation.ui.components.DatePickerSheet
import com.ricardofrango.squadify.presentation.ui.components.FMTopBar
import com.ricardofrango.squadify.presentation.ui.components.PrimaryLoadingButton
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import squadify.app.shared.generated.resources.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock

/** One row of the batch. [key] is a stable identity for Compose iteration since rows can be removed
 * from the middle of the list. [birthDateIso] is null until a date is picked. */
private data class PlayerRow(
    val key: Int,
    val firstName: String = "",
    val lastName: String = "",
    val birthDateIso: String? = null
)

private val FormRoundedShape = RoundedCornerShape(18.dp)

private val DateDisplayFormat = LocalDate.Format {
    day()
    char(' ')
    monthName(MonthNames.ENGLISH_ABBREVIATED)
    char(' ')
    year()
}

/**
 * Batch player creation, matching `CreatePlayersRequest`. Built to the "Add players to the club"
 * artboard of the design canvas linked in AI_GUIDELINES.md ("UI Design Reference"), consistent with
 * the Create Age Groups / Create Teams screens: one card per player with just the fields
 * `CreatePlayer` actually requires -- first name, last name, date of birth -- "+ Add another player",
 * and one CREATE that submits the whole batch -- `CreatePlayersUseCase` writes it in one transaction,
 * so either every player is saved or none are.
 *
 * Deliberately minimal: shirt number, position, height/weight, and emergency contact are all optional
 * on `CreatePlayer`/`Player` but not collected here -- they're meant to be filled in afterwards via a
 * Player Edit screen (not built yet), keeping this quick-add flow to just what's required. Email /
 * parent invites the request also supports aren't exposed here either.
 *
 * Validation is pre-submit and client-side: the CREATE button stays enabled and pressing it surfaces
 * missing names / dates. Any edit clears the shown errors.
 *
 * Rows aren't `rememberSaveable` (same known limitation noted on the other batch screens): a rare
 * mid-edit process kill loses the in-progress batch.
 */
@Composable
fun CreatePlayersScreen(
    clubId: String,
    clubName: String,
    onNavigateBack: () -> Unit,
    onPlayersCreated: () -> Unit,
    viewModel: CreatePlayersViewModel = koinViewModel()
) {
    val uiState by viewModel.state.collectAsState()
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }

    var nextRowKey by remember { mutableStateOf(1) }
    val rows = remember { mutableStateListOf(PlayerRow(key = 0)) }
    var showValidationErrors by remember { mutableStateOf(false) }
    var datePickerForKey by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(viewModel.navigationEvent) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                CreatePlayersNavigationEvent.NavigateBackWithPlayersCreated -> onPlayersCreated()
            }
        }
    }

    CreatePlayersContent(
        uiState = uiState,
        clubName = clubName,
        rows = rows,
        showValidationErrors = showValidationErrors,
        onNavigateBack = onNavigateBack,
        onFirstNameChange = { key, value ->
            updateRow(rows, key) { it.copy(firstName = value) }
            showValidationErrors = false
            viewModel.clearSubmitError()
        },
        onLastNameChange = { key, value ->
            updateRow(rows, key) { it.copy(lastName = value) }
            showValidationErrors = false
            viewModel.clearSubmitError()
        },
        onDateClick = { key -> datePickerForKey = key },
        onAddRow = {
            rows.add(PlayerRow(key = nextRowKey))
            nextRowKey += 1
        },
        onRemoveRow = { key -> rows.removeAll { it.key == key } },
        onCreateClick = {
            showValidationErrors = true
            val allValid = rows.isNotEmpty() && rows.all {
                it.firstName.isNotBlank() && it.lastName.isNotBlank() && it.birthDateIso != null
            }
            if (allValid) {
                val players = rows.map { r ->
                    CreatePlayer(
                        firstName = r.firstName.trim(),
                        lastName = r.lastName.trim(),
                        birthDate = LocalDate.parse(r.birthDateIso!!)
                    )
                }
                viewModel.onCreateClicked(clubId, players)
            }
        }
    )

    val pickerRowKey = datePickerForKey
    if (pickerRowKey != null) {
        val row = rows.firstOrNull { it.key == pickerRowKey }
        DatePickerSheet(
            title = Res.string.label_select_birth_date,
            initialDate = row?.birthDateIso?.let { LocalDate.parse(it) } ?: LocalDate(today.year - 10, 1, 1),
            maxDate = today,
            onConfirm = { picked ->
                updateRow(rows, pickerRowKey) { it.copy(birthDateIso = picked.toString()) }
                datePickerForKey = null
                showValidationErrors = false
                viewModel.clearSubmitError()
            },
            onDismiss = { datePickerForKey = null }
        )
    }
}

private inline fun updateRow(rows: MutableList<PlayerRow>, key: Int, transform: (PlayerRow) -> PlayerRow) {
    val index = rows.indexOfFirst { it.key == key }
    if (index >= 0) rows[index] = transform(rows[index])
}

@Composable
private fun CreatePlayersContent(
    uiState: CreatePlayersUiState,
    clubName: String,
    rows: List<PlayerRow>,
    showValidationErrors: Boolean,
    onNavigateBack: () -> Unit,
    onFirstNameChange: (key: Int, value: String) -> Unit,
    onLastNameChange: (key: Int, value: String) -> Unit,
    onDateClick: (key: Int) -> Unit,
    onAddRow: () -> Unit,
    onRemoveRow: (key: Int) -> Unit,
    onCreateClick: () -> Unit
) {
    val anyNameMissing = showValidationErrors && rows.any { it.firstName.isBlank() || it.lastName.isBlank() }
    val anyDobMissing = showValidationErrors && rows.any { it.birthDateIso == null }
    val selectedCount = rows.size

    Scaffold(
        containerColor = Color.White,
        topBar = {
            FMTopBar(
                title = stringResource(Res.string.title_create_players),
                subtitle = clubName,
                onNavigateBack = onNavigateBack
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.White)
        ) {
            // Fixed header: intro + "add row" -- stays put as the list below grows, instead of
            // scrolling out of reach.
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(Res.string.msg_players_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = DeepCarbon.copy(alpha = 0.6f)
                )
                OutlinedButton(
                    onClick = onAddRow,
                    modifier = Modifier.fillMaxWidth(),
                    shape = FormRoundedShape,
                    border = BorderStroke(1.dp, PitchGreen.copy(alpha = 0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PitchGreen)
                ) {
                    Text("+ ${stringResource(Res.string.label_add_another_player)}")
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                rows.forEachIndexed { index, row ->
                    PlayerRowCard(
                        index = index + 1,
                        row = row,
                        firstNameError = showValidationErrors && row.firstName.isBlank(),
                        lastNameError = showValidationErrors && row.lastName.isBlank(),
                        dobError = showValidationErrors && row.birthDateIso == null,
                        canRemove = rows.size > 1,
                        onFirstNameChange = { onFirstNameChange(row.key, it) },
                        onLastNameChange = { onLastNameChange(row.key, it) },
                        onDateClick = { onDateClick(row.key) },
                        onRemove = { onRemoveRow(row.key) }
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Fixed footer: validation summary + submit -- always reachable without scrolling.
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (anyNameMissing) {
                    Text(
                        text = stringResource(Res.string.msg_player_name_required),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (anyDobMissing) {
                    Text(
                        text = stringResource(Res.string.msg_player_dob_required),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (uiState.error != null) {
                    Text(
                        text = uiState.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                val label = if (selectedCount == 1) {
                    stringResource(Res.string.btn_add_player_one)
                } else {
                    stringResource(Res.string.btn_add_players_other, selectedCount)
                }
                PrimaryLoadingButton(
                    text = label,
                    submittingText = stringResource(Res.string.btn_adding_players),
                    isLoading = uiState.isSubmitting,
                    onClick = onCreateClick
                )

                Text(
                    text = stringResource(Res.string.msg_players_footer_helper),
                    style = MaterialTheme.typography.bodySmall,
                    color = DeepCarbon.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun PlayerRowCard(
    index: Int,
    row: PlayerRow,
    firstNameError: Boolean,
    lastNameError: Boolean,
    dobError: Boolean,
    canRemove: Boolean,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onDateClick: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = FormRoundedShape,
        color = Color.White,
        border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(24.dp).background(CarbonGray.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = index.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = DeepCarbon
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(Res.string.label_player_section).uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = DeepCarbon.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.weight(1f))
                if (canRemove) {
                    Text(
                        text = "✕",
                        color = DeepCarbon.copy(alpha = 0.4f),
                        modifier = Modifier.clickable(onClick = onRemove)
                    )
                }
            }

            NameField(
                value = row.firstName,
                onValueChange = onFirstNameChange,
                label = stringResource(Res.string.label_first_name_field),
                isError = firstNameError
            )
            NameField(
                value = row.lastName,
                onValueChange = onLastNameChange,
                label = stringResource(Res.string.label_last_name_field),
                isError = lastNameError
            )

            Column {
                Text(
                    text = stringResource(Res.string.label_date_of_birth),
                    style = MaterialTheme.typography.labelSmall,
                    color = DeepCarbon.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    onClick = onDateClick,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = FormRoundedShape,
                    color = Color.White,
                    border = BorderStroke(
                        1.dp,
                        if (dobError) MaterialTheme.colorScheme.error else CarbonGray.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val picked = row.birthDateIso?.let { LocalDate.parse(it).format(DateDisplayFormat) }
                        Text(
                            text = picked ?: stringResource(Res.string.label_pick_a_date),
                            color = if (picked != null) DeepCarbon else DeepCarbon.copy(alpha = 0.4f),
                            fontWeight = if (picked != null) FontWeight.Bold else FontWeight.Normal
                        )
                        Text("›", color = DeepCarbon.copy(alpha = 0.4f), fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.msg_date_of_birth_helper),
                    style = MaterialTheme.typography.bodySmall,
                    color = DeepCarbon.copy(alpha = 0.55f)
                )
            }
        }
    }
}

@Composable
private fun NameField(value: String, onValueChange: (String) -> Unit, label: String, isError: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = isError,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = FormRoundedShape,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        colors = fieldColors()
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = DeepCarbon,
    unfocusedTextColor = DeepCarbon,
    focusedBorderColor = PitchGreen,
    unfocusedBorderColor = CarbonGray.copy(alpha = 0.3f),
    focusedLabelColor = PitchGreen,
    unfocusedLabelColor = CarbonGray
)

// ---- Previews ----

@Preview
@Composable
private fun CreatePlayersContentPreview() {
    SquadifyTheme {
        CreatePlayersContent(
            uiState = CreatePlayersUiState(),
            clubName = "CD Ribamar",
            rows = listOf(
                PlayerRow(key = 0, firstName = "Afonso", lastName = "Rebelo", birthDateIso = "2015-03-14"),
                PlayerRow(key = 1)
            ),
            showValidationErrors = false,
            onNavigateBack = {},
            onFirstNameChange = { _, _ -> },
            onLastNameChange = { _, _ -> },
            onDateClick = {},
            onAddRow = {},
            onRemoveRow = {},
            onCreateClick = {}
        )
    }
}

@Preview
@Composable
private fun CreatePlayersContentErrorsPreview() {
    SquadifyTheme(darkTheme = true) {
        CreatePlayersContent(
            uiState = CreatePlayersUiState(error = "Something went wrong. Please try again."),
            clubName = "CD Ribamar",
            rows = listOf(PlayerRow(key = 0)),
            showValidationErrors = true,
            onNavigateBack = {},
            onFirstNameChange = { _, _ -> },
            onLastNameChange = { _, _ -> },
            onDateClick = {},
            onAddRow = {},
            onRemoveRow = {},
            onCreateClick = {}
        )
    }
}

@Preview(widthDp = 780, heightDp = 360)
@Composable
private fun CreatePlayersContentLandscapePreview() {
    SquadifyTheme {
        CreatePlayersContent(
            uiState = CreatePlayersUiState(isSubmitting = true),
            clubName = "CD Ribamar",
            rows = listOf(PlayerRow(key = 0, firstName = "Afonso", lastName = "Rebelo", birthDateIso = "2015-03-14")),
            showValidationErrors = false,
            onNavigateBack = {},
            onFirstNameChange = { _, _ -> },
            onLastNameChange = { _, _ -> },
            onDateClick = {},
            onAddRow = {},
            onRemoveRow = {},
            onCreateClick = {}
        )
    }
}
