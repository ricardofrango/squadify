package com.ricardofrango.clubmanager.presentation.ui.createteams

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
import com.ricardofrango.clubmanager.model.AgeGroup
import com.ricardofrango.clubmanager.model.Season
import com.ricardofrango.clubmanager.model.SeasonType
import com.ricardofrango.clubmanager.model.TeamItemRequest
import com.ricardofrango.clubmanager.presentation.ui.components.FMTopBar
import com.ricardofrango.clubmanager.presentation.ui.components.PrimaryLoadingButton
import com.ricardofrango.clubmanager.presentation.ui.theme.CarbonGray
import com.ricardofrango.clubmanager.presentation.ui.theme.DeepCarbon
import com.ricardofrango.clubmanager.presentation.ui.theme.ClubManagerTheme
import com.ricardofrango.clubmanager.presentation.ui.theme.PitchGreen
import clubmanager.app.shared.generated.resources.*
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** One row of the batch. [key] is a stable identity for Compose iteration since rows can be removed
 * from the middle of the list (a plain index would shift and confuse item state). */
private data class TeamRow(
    val key: Int,
    val name: String = ""
)

private val FormRoundedShape = RoundedCornerShape(18.dp)

/**
 * Batch team creation, matching `CreateTeamsRequest` (a list of name items under one
 * clubId/seasonId/ageGroupId). Built to the "Create teams" artboard of the design canvas linked in
 * AI_GUIDELINES.md ("UI Design Reference"), consistent with the Create Season / Create Age Groups
 * screens: a season picker defaulting to the most recent season (by end date), an age-group picker
 * with no default (an error is shown on submit if it's left unpicked), then one card per team with a
 * mandatory name (an error is shown on any blank name), "+ Add another team", and one CREATE that
 * submits the whole batch -- CreateTeamsUseCase writes it inside one `runInTransaction`, so either
 * every team is saved or none are.
 *
 * Validation is pre-submit and client-side: the CREATE button stays enabled and pressing it surfaces
 * the missing age group / blank names rather than silently doing nothing. Any edit clears the shown
 * errors.
 *
 * Rows aren't `rememberSaveable`: preserving a list of a custom row type across process death needs a
 * custom Saver this project doesn't have yet, so a rare mid-edit process kill loses the in-progress
 * batch. Disclosed as a known simplification, not a silent gap.
 */
@Composable
fun CreateTeamsScreen(
    clubId: String,
    clubName: String,
    onNavigateBack: () -> Unit,
    onTeamsCreated: () -> Unit,
    viewModel: CreateTeamsViewModel = koinViewModel()
) {
    val uiState by viewModel.state.collectAsState()

    var nextRowKey by remember { mutableStateOf(1) }
    val rows = remember { mutableStateListOf(TeamRow(key = 0)) }
    // Flipped true by the first CREATE press; drives the age-group and blank-name error styling until
    // the user edits something.
    var showValidationErrors by remember { mutableStateOf(false) }

    LaunchedEffect(clubId) {
        viewModel.load(clubId)
    }

    LaunchedEffect(viewModel.navigationEvent) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                CreateTeamsNavigationEvent.NavigateBackWithTeamsCreated -> onTeamsCreated()
            }
        }
    }

    CreateTeamsContent(
        uiState = uiState,
        clubName = clubName,
        rows = rows,
        showValidationErrors = showValidationErrors,
        onNavigateBack = onNavigateBack,
        onSeasonSelected = { seasonId ->
            viewModel.onSeasonSelected(seasonId)
            showValidationErrors = false
        },
        onAgeGroupSelected = { ageGroupId ->
            viewModel.onAgeGroupSelected(ageGroupId)
            showValidationErrors = false
        },
        onRowNameChanged = { rowKey, name ->
            val index = rows.indexOfFirst { it.key == rowKey }
            if (index >= 0) rows[index] = rows[index].copy(name = name)
            showValidationErrors = false
            viewModel.clearSubmitError()
        },
        onAddRow = {
            rows.add(TeamRow(key = nextRowKey))
            nextRowKey += 1
        },
        onRemoveRow = { rowKey ->
            rows.removeAll { it.key == rowKey }
        },
        onCreateClick = {
            showValidationErrors = true
            val idle = uiState as? CreateTeamsUiState.Idle
            val ageGroupPicked = idle?.selectedAgeGroupId != null
            val namesFilled = rows.isNotEmpty() && rows.all { it.name.isNotBlank() }
            if (ageGroupPicked && namesFilled) {
                viewModel.onCreateClicked(clubId, rows.map { TeamItemRequest(name = it.name.trim()) })
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateTeamsContent(
    uiState: CreateTeamsUiState,
    clubName: String,
    rows: List<TeamRow>,
    showValidationErrors: Boolean,
    onNavigateBack: () -> Unit,
    onSeasonSelected: (seasonId: String) -> Unit,
    onAgeGroupSelected: (ageGroupId: String) -> Unit,
    onRowNameChanged: (rowKey: Int, name: String) -> Unit,
    onAddRow: () -> Unit,
    onRemoveRow: (rowKey: Int) -> Unit,
    onCreateClick: () -> Unit
) {
    Scaffold(
        containerColor = Color.White,
        topBar = {
            FMTopBar(
                title = stringResource(Res.string.title_create_teams),
                subtitle = clubName,
                onNavigateBack = onNavigateBack
            )
        }
    ) { paddingValues ->
        when (uiState) {
            is CreateTeamsUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues).background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PitchGreen)
                }
            }

            is CreateTeamsUiState.Idle -> if (uiState.seasons.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues).background(Color.White).padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(Res.string.msg_no_seasons_for_teams),
                        style = MaterialTheme.typography.bodyMedium,
                        color = DeepCarbon.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                val ageGroupsForSeason = uiState.ageGroupsForSeason
                val ageGroupMissing = showValidationErrors && uiState.selectedAgeGroupId == null
                val anyBlankName = showValidationErrors && rows.any { it.name.isBlank() }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .background(Color.White)
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    PickerField(
                        label = stringResource(Res.string.label_season_field),
                        selectedText = uiState.selectedSeason?.name,
                        placeholder = stringResource(Res.string.label_choose_season),
                        options = uiState.seasons.map { it.id to it.name },
                        isError = false,
                        onOptionSelected = onSeasonSelected
                    )

                    PickerField(
                        label = stringResource(Res.string.label_age_group_field),
                        selectedText = ageGroupsForSeason.find { it.id == uiState.selectedAgeGroupId }?.name,
                        placeholder = stringResource(Res.string.label_choose_age_group),
                        options = ageGroupsForSeason.map { it.id to it.name },
                        isError = ageGroupMissing,
                        onOptionSelected = onAgeGroupSelected
                    )

                    if (ageGroupsForSeason.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.msg_season_has_no_age_groups),
                            style = MaterialTheme.typography.bodySmall,
                            color = DeepCarbon.copy(alpha = 0.6f)
                        )
                    } else if (ageGroupMissing) {
                        Text(
                            text = stringResource(Res.string.msg_select_age_group_error),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Text(
                        text = stringResource(Res.string.msg_teams_intro),
                        style = MaterialTheme.typography.bodySmall,
                        color = DeepCarbon.copy(alpha = 0.6f)
                    )

                    rows.forEachIndexed { index, row ->
                        TeamRowCard(
                            index = index + 1,
                            row = row,
                            isError = showValidationErrors && row.name.isBlank(),
                            canRemove = rows.size > 1,
                            onNameChanged = { onRowNameChanged(row.key, it) },
                            onRemove = { onRemoveRow(row.key) }
                        )
                    }

                    if (anyBlankName) {
                        Text(
                            text = stringResource(Res.string.msg_team_name_required),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    OutlinedButton(
                        onClick = onAddRow,
                        modifier = Modifier.fillMaxWidth(),
                        shape = FormRoundedShape,
                        border = BorderStroke(1.dp, PitchGreen.copy(alpha = 0.4f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PitchGreen)
                    ) {
                        Text("+ ${stringResource(Res.string.label_add_another_team)}")
                    }

                    if (uiState.error != null) {
                        Text(
                            text = uiState.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    PrimaryLoadingButton(
                        text = Res.string.btn_create_teams,
                        submittingText = Res.string.btn_creating_teams,
                        isLoading = uiState.isSubmitting,
                        onClick = onCreateClick
                    )

                    Text(
                        text = stringResource(Res.string.msg_teams_footer_helper),
                        style = MaterialTheme.typography.bodySmall,
                        color = DeepCarbon.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerField(
    label: String,
    selectedText: String?,
    placeholder: String,
    options: List<Pair<String, String>>,
    isError: Boolean,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val borderColor = when {
        isError -> MaterialTheme.colorScheme.error
        else -> CarbonGray.copy(alpha = 0.3f)
    }
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = DeepCarbon.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box {
            Surface(
                onClick = { if (options.isNotEmpty()) expanded = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = FormRoundedShape,
                color = Color.White,
                border = BorderStroke(1.dp, borderColor)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = selectedText ?: placeholder,
                        color = if (selectedText != null) DeepCarbon else DeepCarbon.copy(alpha = 0.4f),
                        fontWeight = if (selectedText != null) FontWeight.Bold else FontWeight.Normal
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
                        onClick = {
                            onOptionSelected(value)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TeamRowCard(
    index: Int,
    row: TeamRow,
    isError: Boolean,
    canRemove: Boolean,
    onNameChanged: (String) -> Unit,
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
                    text = stringResource(Res.string.label_team_section).uppercase(),
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

            OutlinedTextField(
                value = row.name,
                onValueChange = onNameChanged,
                label = { Text(stringResource(Res.string.label_team_name_field)) },
                placeholder = { Text(stringResource(Res.string.label_team_name_placeholder)) },
                isError = isError,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = FormRoundedShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
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
    }
}

private fun previewSeason(id: String, name: String) = Season(
    id = id,
    clubId = "club-1",
    name = name,
    type = SeasonType.EUROPEAN_YEAR,
    startDate = LocalDate(2026, 7, 1),
    endDate = LocalDate(2027, 6, 30),
    active = true
)

private fun previewAgeGroup(id: String, name: String, seasonId: String) = AgeGroup(
    id = id,
    clubId = "club-1",
    name = name,
    seasonId = seasonId,
    birthYear = 2015
)

@Preview
@Composable
private fun CreateTeamsContentPreview() {
    ClubManagerTheme {
        CreateTeamsContent(
            uiState = CreateTeamsUiState.Idle(
                seasons = listOf(previewSeason("s1", "26/27"), previewSeason("s2", "25/26")),
                selectedSeasonId = "s1",
                ageGroups = listOf(previewAgeGroup("a1", "Sub 11", "s1"), previewAgeGroup("a2", "Sub 13", "s1")),
                selectedAgeGroupId = "a1"
            ),
            clubName = "CD Ribamar",
            rows = listOf(TeamRow(key = 0, name = "Team A"), TeamRow(key = 1)),
            showValidationErrors = false,
            onNavigateBack = {},
            onSeasonSelected = {},
            onAgeGroupSelected = {},
            onRowNameChanged = { _, _ -> },
            onAddRow = {},
            onRemoveRow = {},
            onCreateClick = {}
        )
    }
}

@Preview
@Composable
private fun CreateTeamsContentValidationErrorsPreview() {
    ClubManagerTheme(darkTheme = true) {
        CreateTeamsContent(
            uiState = CreateTeamsUiState.Idle(
                seasons = listOf(previewSeason("s1", "26/27")),
                selectedSeasonId = "s1",
                ageGroups = listOf(previewAgeGroup("a1", "Sub 11", "s1")),
                selectedAgeGroupId = null
            ),
            clubName = "CD Ribamar",
            rows = listOf(TeamRow(key = 0, name = "")),
            showValidationErrors = true,
            onNavigateBack = {},
            onSeasonSelected = {},
            onAgeGroupSelected = {},
            onRowNameChanged = { _, _ -> },
            onAddRow = {},
            onRemoveRow = {},
            onCreateClick = {}
        )
    }
}

@Preview(widthDp = 780, heightDp = 360)
@Composable
private fun CreateTeamsContentLandscapePreview() {
    ClubManagerTheme {
        CreateTeamsContent(
            uiState = CreateTeamsUiState.Idle(
                seasons = listOf(previewSeason("s1", "26/27")),
                selectedSeasonId = "s1",
                ageGroups = listOf(previewAgeGroup("a1", "Sub 11", "s1")),
                selectedAgeGroupId = "a1",
                isSubmitting = true
            ),
            clubName = "CD Ribamar",
            rows = listOf(TeamRow(key = 0, name = "Team A")),
            showValidationErrors = false,
            onNavigateBack = {},
            onSeasonSelected = {},
            onAgeGroupSelected = {},
            onRowNameChanged = { _, _ -> },
            onAddRow = {},
            onRemoveRow = {},
            onCreateClick = {}
        )
    }
}
