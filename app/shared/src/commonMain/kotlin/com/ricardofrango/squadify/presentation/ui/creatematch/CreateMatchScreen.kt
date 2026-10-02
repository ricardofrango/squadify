package com.ricardofrango.squadify.presentation.ui.creatematch

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
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
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
 * button, so there is no permission state -- the server still enforces `minTeamRole = COACH`.
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
    viewModel: CreateMatchViewModel = koinViewModel()
) {
    val uiState by viewModel.state.collectAsState()

    LaunchedEffect(ageGroupId, teamId) { viewModel.load(ageGroupId, teamId) }
    LaunchedEffect(viewModel.navigationEvent) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                CreateMatchNavigationEvent.NavigateBackWithMatchCreated -> onMatchCreated()
            }
        }
    }

    CreateMatchContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onRetry = { viewModel.load(ageGroupId, teamId) },
        onTeamSelected = viewModel::onTeamSelected,
        onOpponentSelected = viewModel::onOpponentSelected,
        onStartAddOpponent = viewModel::onStartAddOpponent,
        onCancelAddOpponent = viewModel::onCancelAddOpponent,
        onNewOpponentNameChange = viewModel::onNewOpponentNameChange,
        onDateChange = viewModel::onDateChange,
        onTimeChange = viewModel::onTimeChange,
        onLocationChange = viewModel::onLocationChange,
        onNotesChange = viewModel::onNotesChange,
        onMatchTypeSelected = viewModel::onMatchTypeSelected,
        onHalvesSelected = viewModel::onHalvesSelected,
        onSubmit = viewModel::onSubmit
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateMatchContent(
    uiState: CreateMatchUiState,
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit,
    onTeamSelected: (String) -> Unit,
    onOpponentSelected: (String) -> Unit,
    onStartAddOpponent: () -> Unit,
    onCancelAddOpponent: () -> Unit,
    onNewOpponentNameChange: (String) -> Unit,
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
                onOpponentSelected = onOpponentSelected,
                onStartAddOpponent = onStartAddOpponent,
                onCancelAddOpponent = onCancelAddOpponent,
                onNewOpponentNameChange = onNewOpponentNameChange,
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
    onOpponentSelected: (String) -> Unit,
    onStartAddOpponent: () -> Unit,
    onCancelAddOpponent: () -> Unit,
    onNewOpponentNameChange: (String) -> Unit,
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
            FieldCard(label = stringResource(Res.string.label_opponent_field), required = true) {
                if (state.addingOpponent) {
                    OutlinedTextField(
                        value = state.newOpponentName,
                        onValueChange = onNewOpponentNameChange,
                        label = { Text(stringResource(Res.string.label_new_opponent_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = CardShape,
                        colors = fieldColors()
                    )
                    Spacer(Modifier.height(4.dp))
                    LinkText(stringResource(Res.string.btn_cancel), onClick = onCancelAddOpponent)
                } else {
                    Picker(
                        selectedText = state.opponents.find { it.id == state.selectedOpponentId }?.let { it.shortName ?: it.name },
                        placeholder = stringResource(Res.string.label_choose_opponent),
                        options = state.opponents.map { it.id to (it.shortName ?: it.name) },
                        onOptionSelected = onOpponentSelected
                    )
                    Spacer(Modifier.height(8.dp))
                    LinkText("+ ${stringResource(Res.string.label_new_opponent)}", onClick = onStartAddOpponent)
                }
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
private fun LinkText(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
        color = PitchGreen,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
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
    opponents = listOf(Opponent("o1", "c1", "AD Fontelas", "AD Fontelas", null)),
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
            onNavigateBack = {}, onRetry = {}, onTeamSelected = {}, onOpponentSelected = {},
            onStartAddOpponent = {}, onCancelAddOpponent = {}, onNewOpponentNameChange = {},
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
            onNavigateBack = {}, onRetry = {}, onTeamSelected = {}, onOpponentSelected = {},
            onStartAddOpponent = {}, onCancelAddOpponent = {}, onNewOpponentNameChange = {},
            onDateChange = {}, onTimeChange = {}, onLocationChange = {}, onNotesChange = {}, onMatchTypeSelected = {}, onSubmit = {}
        )
    }
}
