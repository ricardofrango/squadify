package com.ricardofrango.squadify.presentation.ui.creatematch

import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import com.ricardofrango.squadify.presentation.navigation.collectSettledState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.model.Opponent
import com.ricardofrango.squadify.presentation.ui.components.HalvesSelector
import com.ricardofrango.squadify.presentation.ui.components.MatchTypeSelector
import com.ricardofrango.squadify.model.MatchType
import com.ricardofrango.squadify.model.Team
import com.ricardofrango.squadify.presentation.ui.components.DatePickerSheet
import com.ricardofrango.squadify.presentation.ui.components.FMTopBar
import com.ricardofrango.squadify.presentation.ui.components.PrimaryLoadingButton
import com.ricardofrango.squadify.presentation.ui.components.TimePickerSheet
import com.ricardofrango.squadify.presentation.ui.opponents.OpponentLogo
import com.ricardofrango.squadify.presentation.ui.opponents.opponentSubline
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyClubTheme
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import squadify.app.shared.generated.resources.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private val CardShape = RoundedCornerShape(18.dp)

private val DayMonthYear = LocalDate.Format {
    day(); char(' '); monthName(MonthNames.ENGLISH_ABBREVIATED); char(' '); year()
}

/**
 * Schedule one fixture, matching `CreateMatchRequest`. Built to the "Schedule match" artboard
 * (13A / 13B) of the design canvas in AI_GUIDELINES.md. Reached only from a `canManage`-gated
 * button, so there is no permission state -- the server still enforces `minAgeGroupRole = COACH`.
 *
 * Two entry shapes: from an age group the team is chosen here (from that age group's teams); from a
 * team it's fixed and shown locked. The age group / season are read-only context in both.
 */
@Composable
fun CreateMatchScreen(
    ageGroupId: String?,
    teamId: String?,
    onNavigateBack: () -> Unit,
    onMatchCreated: () -> Unit,
    // Opens the Opponents screen to pick one (clubId, ageGroupId, the current pick).
    onChooseOpponent: (clubId: String, ageGroupId: String, selectedOpponentId: String?) -> Unit,
    // The opponent just picked there, handed back once; [onPickedOpponentConsumed] clears it.
    pickedOpponentId: String? = null,
    onPickedOpponentConsumed: () -> Unit = {},
    // Set when that opponent was just created there: shown as "… created and selected", then cleared.
    createdOpponentName: String? = null,
    onCreatedOpponentShown: () -> Unit = {},
    viewModel: CreateMatchViewModel = koinViewModel()
) {
    SquadifyClubTheme {
        val uiState = viewModel.state.collectSettledState()
        val snackbarHostState = remember { SnackbarHostState() }
        val createdMessage = createdOpponentName?.let { stringResource(Res.string.msg_opponent_created_selected, it) }
        LaunchedEffect(createdMessage) {
            if (createdMessage != null) {
                onCreatedOpponentShown()
                snackbarHostState.showSnackbar(createdMessage)
            }
        }

        LaunchedEffect(ageGroupId, teamId) { viewModel.load(ageGroupId, teamId) }
        LaunchedEffect(pickedOpponentId) {
            if (pickedOpponentId != null) {
                viewModel.onOpponentPicked(pickedOpponentId)
                onPickedOpponentConsumed()
            }
        }
        LaunchedEffect(viewModel.navigationEvent) {
            viewModel.navigationEvent.collect { event ->
                when (event) {
                    CreateMatchNavigationEvent.NavigateBackWithMatchCreated -> onMatchCreated()
                }
            }
        }

        CreateMatchContent(
            uiState = uiState,
            snackbarHostState = snackbarHostState,
            onNavigateBack = onNavigateBack,
            onRetry = { viewModel.load(ageGroupId, teamId) },
            onTeamSelected = viewModel::onTeamSelected,
            onChooseOpponent = { state -> onChooseOpponent(state.clubId, state.ageGroupId, state.selectedOpponentId) },
            onDateChange = viewModel::onDateChange,
            onTimeChange = viewModel::onTimeChange,
            onLocationChange = viewModel::onLocationChange,
            onNotesChange = viewModel::onNotesChange,
            onMatchTypeSelected = viewModel::onMatchTypeSelected,
            onHalvesSelected = viewModel::onHalvesSelected,
            onSubmit = viewModel::onSubmit
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateMatchContent(
    uiState: CreateMatchUiState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit,
    onTeamSelected: (String) -> Unit,
    onChooseOpponent: (CreateMatchUiState.Idle) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onTimeChange: (LocalTime) -> Unit,
    onLocationChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onMatchTypeSelected: (MatchType) -> Unit,
    onHalvesSelected: (Int) -> Unit = {},
    onSubmit: () -> Unit
) {
    val clubName = (uiState as? CreateMatchUiState.Idle)?.clubName
    Scaffold(
        containerColor = Color.White,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            FMTopBar(
                title = stringResource(Res.string.title_schedule_match),
                subtitle = clubName,
                onNavigateBack = onNavigateBack
            )
        }
    ) { paddingValues ->
        val boxModifier = Modifier.fillMaxSize().padding(paddingValues).background(Color.White)
        when (uiState) {
            is CreateMatchUiState.Loading ->
                Box(boxModifier, contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PitchGreen) }

            is CreateMatchUiState.Error ->
                Column(
                    boxModifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(uiState.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRetry) { Text(stringResource(Res.string.btn_retry)) }
                }

            is CreateMatchUiState.Idle -> CreateMatchForm(
                state = uiState,
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                onTeamSelected = onTeamSelected,
                onChooseOpponent = { onChooseOpponent(uiState) },
                onDateChange = onDateChange,
                onTimeChange = onTimeChange,
                onLocationChange = onLocationChange,
                onNotesChange = onNotesChange,
                onMatchTypeSelected = onMatchTypeSelected,
                onHalvesSelected = onHalvesSelected,
                onSubmit = onSubmit
            )
        }
    }
}

@Composable
private fun CreateMatchForm(
    state: CreateMatchUiState.Idle,
    modifier: Modifier,
    onTeamSelected: (String) -> Unit,
    onChooseOpponent: () -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onTimeChange: (LocalTime) -> Unit,
    onLocationChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onMatchTypeSelected: (MatchType) -> Unit,
    onHalvesSelected: (Int) -> Unit = {},
    onSubmit: () -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val contextCaption = "${state.ageGroupName} · ${state.seasonName}"

    Column(modifier = modifier.background(Color.White)) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ---- Team ----
            FieldCard(label = stringResource(Res.string.label_team_field), required = true) {
                if (state.teamFixed) {
                    LockedTeamRow(team = state.selectedTeam, contextCaption = contextCaption)
                } else {
                    Text(
                        contextCaption,
                        style = MaterialTheme.typography.bodySmall,
                        color = DeepCarbon.copy(alpha = 0.55f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Picker(
                        selectedText = state.selectedTeam?.name,
                        placeholder = stringResource(Res.string.label_choose_team),
                        options = state.teams.map { it.id to it.name },
                        onOptionSelected = onTeamSelected
                    )
                }
            }

            // ---- Opponent ----
            // Opens the searchable Opponents list (design 38h); creating one happens there.
            FieldCard(label = stringResource(Res.string.label_opponent_field), required = true) {
                OpponentField(opponent = state.selectedOpponent, onClick = onChooseOpponent)
            }

            // ---- Match type (required, no default) ----
            FieldCard(label = stringResource(Res.string.label_match_type), required = true) {
                MatchTypeSelector(selected = state.matchType, onSelected = onMatchTypeSelected, error = state.matchTypeError)
            }

            // ---- Halves (two by default; one is a single period with no half time) ----
            FieldCard(label = stringResource(Res.string.label_halves), required = false) {
                HalvesSelector(halves = state.halves, onSelected = onHalvesSelected)
            }

            // ---- When ----
            FieldCard(label = stringResource(Res.string.label_when_field), required = true) {
                ValueField(
                    label = stringResource(Res.string.label_match_date),
                    value = state.date.format(DayMonthYear),
                    onClick = { showDatePicker = true }
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_calendar),
                        contentDescription = null,
                        tint = DeepCarbon.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                ValueField(
                    label = stringResource(Res.string.label_match_kickoff),
                    value = formatTime(state.time),
                    onClick = { showTimePicker = true }
                ) {
                    Text("▾", color = DeepCarbon.copy(alpha = 0.4f), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(Res.string.msg_match_when_helper),
                    style = MaterialTheme.typography.bodySmall,
                    color = DeepCarbon.copy(alpha = 0.5f)
                )
            }

            // ---- Details ----
            FieldCard(label = stringResource(Res.string.label_details_section), required = false) {
                OutlinedTextField(
                    value = state.location,
                    onValueChange = onLocationChange,
                    label = { Text(stringResource(Res.string.label_match_location)) },
                    placeholder = { Text(stringResource(Res.string.label_match_location_placeholder)) },
                    singleLine = true,
                    // Next moves to Notes. Notes is multi-line, so its Enter key adds a line rather than submitting.
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShape,
                    colors = fieldColors()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = onNotesChange,
                    label = { Text(stringResource(Res.string.label_match_notes)) },
                    placeholder = { Text(stringResource(Res.string.label_match_notes_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    shape = CardShape,
                    colors = fieldColors()
                )
            }

            if (state.error != null) {
                Text(
                    state.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        // Pinned action bar
        Surface(
            color = Color.White,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            // No navigationBarsPadding() here: this Surface sits inside the Column that's already
            // padded by Scaffold's own paddingValues, which (with no bottomBar slot in use) already
            // reserves the bottom system-bar inset -- adding it again pushed the button up an extra
            // inset's worth, leaving a large empty gap below it.
            Box(Modifier.padding(24.dp)) {
                PrimaryLoadingButton(
                    text = Res.string.btn_schedule_match,
                    submittingText = Res.string.btn_scheduling_match,
                    isLoading = state.isSubmitting,
                    enabled = state.canSubmit,
                    onClick = onSubmit
                )
                // The button stays disabled until a type is picked, but a tap on it still has to say
                // why (design 28b): onSubmit flags the missing type instead of scheduling.
                if (!state.canSubmit && state.matchType == null) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onSubmit)
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        DatePickerSheet(
            title = Res.string.label_select_match_date,
            initialDate = state.date,
            onConfirm = { showDatePicker = false; onDateChange(it) },
            onDismiss = { showDatePicker = false }
        )
    }
    if (showTimePicker) {
        TimePickerSheet(
            title = Res.string.label_select_kickoff_time,
            initialTime = state.time,
            onConfirm = { showTimePicker = false; onTimeChange(it) },
            onDismiss = { showTimePicker = false }
        )
    }
}

// ---- Pieces ----

@Composable
private fun FieldCard(label: String, required: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        color = Color.White,
        border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = DeepCarbon.copy(alpha = 0.5f)
                )
                if (required) {
                    Spacer(Modifier.width(4.dp))
                    Text("*", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun LockedTeamRow(team: Team?, contextCaption: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(40.dp).background(PitchGreen, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                team?.code ?: team?.name?.takeLast(1) ?: "?",
                color = Color.White, fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                team?.name ?: "—",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = DeepCarbon, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(contextCaption, style = MaterialTheme.typography.bodySmall, color = DeepCarbon.copy(alpha = 0.55f))
        }
        Text("🔒", color = DeepCarbon.copy(alpha = 0.35f))
    }
}

@Composable
private fun Picker(
    selectedText: String?,
    placeholder: String,
    options: List<Pair<String, String>>,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { if (options.isNotEmpty()) expanded = true },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = CardShape,
            color = Color.White,
            border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = selectedText ?: placeholder,
                    color = if (selectedText != null) DeepCarbon else DeepCarbon.copy(alpha = 0.4f),
                    fontWeight = if (selectedText != null) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text("▾", color = DeepCarbon.copy(alpha = 0.4f), fontWeight = FontWeight.Bold)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color.White)
        ) {
            options.forEach { (value, name) ->
                DropdownMenuItem(
                    text = { Text(name, color = DeepCarbon) },
                    onClick = { onOptionSelected(value); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun OpponentField(opponent: Opponent?, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = CardShape,
        color = Color.White,
        border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (opponent != null) {
                OpponentLogo(opponent.name, opponent.shortName, opponent.logoUrl, size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        opponent.name,
                        color = DeepCarbon, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    opponentSubline(opponent)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = DeepCarbon.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            } else {
                Text(
                    stringResource(Res.string.title_choose_opponent),
                    color = DeepCarbon.copy(alpha = 0.4f),
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                )
            }
            Icon(
                painter = painterResource(Res.drawable.ic_chevron_right),
                contentDescription = null,
                tint = DeepCarbon.copy(alpha = 0.35f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ValueField(label: String, value: String, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        color = Color.White,
        border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = DeepCarbon.copy(alpha = 0.5f))
                Text(value, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
            }
            trailing()
        }
    }
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

private fun formatTime(time: LocalTime): String =
    "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"

// ---- Previews ----

private fun sampleIdle(teamFixed: Boolean) = CreateMatchUiState.Idle(
    clubId = "c1",
    clubName = "CD Ribamar",
    ageGroupId = "ag1",
    ageGroupName = "Sub-15",
    seasonName = "26/27",
    teamFixed = teamFixed,
    teams = listOf(
        Team("t1", "c1", "ag1", "s1", "Sub-15 A", "A", true),
        Team("t2", "c1", "ag1", "s1", "Sub-15 B", "B", true)
    ),
    selectedTeamId = if (teamFixed) "t1" else null,
    opponents = listOf(Opponent("o1", "c1", "AD Fontelas", "ADF", null)),
    selectedOpponentId = if (teamFixed) "o1" else null,
    date = LocalDate(2026, 9, 12),
    time = LocalTime(10, 30),
    location = if (teamFixed) "Campo do Ribamar" else ""
)

@Preview
@Composable
private fun CreateMatchFromAgeGroupPreview() {
    SquadifyTheme {
        CreateMatchContent(
            uiState = sampleIdle(teamFixed = false),
            onNavigateBack = {}, onRetry = {}, onTeamSelected = {}, onChooseOpponent = {},
            onDateChange = {}, onTimeChange = {}, onLocationChange = {}, onNotesChange = {}, onMatchTypeSelected = {}, onSubmit = {}
        )
    }
}

@Preview
@Composable
private fun CreateMatchFromTeamPreview() {
    SquadifyTheme {
        CreateMatchContent(
            uiState = sampleIdle(teamFixed = true),
            onNavigateBack = {}, onRetry = {}, onTeamSelected = {}, onChooseOpponent = {},
            onDateChange = {}, onTimeChange = {}, onLocationChange = {}, onNotesChange = {}, onMatchTypeSelected = {}, onSubmit = {}
        )
    }
}
