package com.ricardofrango.squadify.presentation.ui.createteams

import com.ricardofrango.squadify.presentation.ui.theme.ScreenBackground
import androidx.compose.foundation.text.KeyboardActions
import com.ricardofrango.squadify.presentation.ui.components.submitKeyboardActions
import com.ricardofrango.squadify.presentation.navigation.collectSettledState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.Season
import com.ricardofrango.squadify.model.SeasonType
import com.ricardofrango.squadify.model.SportType
import com.ricardofrango.squadify.model.TeamItemRequest
import com.ricardofrango.squadify.presentation.ui.components.SquadifyTopBar
import com.ricardofrango.squadify.presentation.ui.components.PinnedBottomBar
import com.ricardofrango.squadify.presentation.ui.components.PrimaryLoadingButton
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyClubTheme
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.screenContentPadding
import squadify.app.shared.generated.resources.*
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** One row of the batch. [key] is a stable identity for Compose iteration since rows can be removed
 * from the middle of the list (a plain index would shift and confuse item state). */
private data class TeamRow(
    val key: Int,
    val name: String = "",
    // The a-side count this team plays. Pre-filled from the picked age group's default (or the sport's
    // largest format when the age group predates team sizes) and overridable per row; sent as
    // `TeamItemRequest.teamSize`.
    val teamSize: Int? = null
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
    // The club's sport -- its `teamSizes` are the options for the per-row team-size picker.
    sport: SportType,
    onNavigateBack: () -> Unit,
    onTeamsCreated: () -> Unit,
    initialAgeGroupId: String? = null,
    viewModel: CreateTeamsViewModel = koinViewModel()
) {
    SquadifyClubTheme {
        val uiState = viewModel.state.collectSettledState()

        // The team-size a new row starts on: the picked age group's default, or the sport's largest format
        // when no age group is picked yet / that age group predates team sizes.
        val defaultTeamSize = (uiState as? CreateTeamsUiState.Idle)?.let { idle ->
            idle.ageGroupsForSeason.find { it.id == idle.selectedAgeGroupId }?.teamSize
        } ?: sport.teamSizes.last()

        var nextRowKey by remember { mutableStateOf(1) }
        val rows = remember { mutableStateListOf(TeamRow(key = 0)) }
        // Flipped true by the first CREATE press; drives the age-group and blank-name error styling until
        // the user edits something.
        var showValidationErrors by remember { mutableStateOf(false) }

        LaunchedEffect(clubId) {
            viewModel.load(clubId, initialAgeGroupId)
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
            teamSizeOptions = sport.teamSizes,
            showValidationErrors = showValidationErrors,
            onNavigateBack = onNavigateBack,
            onSeasonSelected = { seasonId ->
                viewModel.onSeasonSelected(seasonId)
                // Selecting a season clears the age group, so its team-size default is gone too -- reset
                // the rows to the sport's largest format until an age group is picked again.
                for (i in rows.indices) rows[i] = rows[i].copy(teamSize = sport.teamSizes.last())
                showValidationErrors = false
            },
            onAgeGroupSelected = { ageGroupId ->
                viewModel.onAgeGroupSelected(ageGroupId)
                // Re-seed every row with the newly picked age group's default team size (overridable
                // afterwards). Mirrors the birth-year reset on season change in CreateAgeGroupsScreen.
                val ageGroup = (uiState as? CreateTeamsUiState.Idle)?.ageGroups?.find { it.id == ageGroupId }
                val newDefault = ageGroup?.teamSize ?: sport.teamSizes.last()
                for (i in rows.indices) rows[i] = rows[i].copy(teamSize = newDefault)
                showValidationErrors = false
            },
            onRowNameChanged = { rowKey, name ->
                val index = rows.indexOfFirst { it.key == rowKey }
                if (index >= 0) rows[index] = rows[index].copy(name = name)
                showValidationErrors = false
                viewModel.clearSubmitError()
            },
            onRowTeamSizeSelected = { rowKey, teamSize ->
                val index = rows.indexOfFirst { it.key == rowKey }
                if (index >= 0) rows[index] = rows[index].copy(teamSize = teamSize)
                viewModel.clearSubmitError()
            },
            onAddRow = {
                rows.add(TeamRow(key = nextRowKey, teamSize = defaultTeamSize))
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
                    viewModel.onCreateClicked(
                        clubId,
                        rows.map { TeamItemRequest(name = it.name.trim(), teamSize = it.teamSize) }
                    )
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateTeamsContent(
    uiState: CreateTeamsUiState,
    clubName: String,
    rows: List<TeamRow>,
    teamSizeOptions: List<Int>,
    showValidationErrors: Boolean,
    onNavigateBack: () -> Unit,
    onSeasonSelected: (seasonId: String) -> Unit,
    onAgeGroupSelected: (ageGroupId: String) -> Unit,
    onRowNameChanged: (rowKey: Int, name: String) -> Unit,
    onRowTeamSizeSelected: (rowKey: Int, teamSize: Int) -> Unit,
    onAddRow: () -> Unit,
    onRemoveRow: (rowKey: Int) -> Unit,
    onCreateClick: () -> Unit
) {
    Scaffold(
        containerColor = ScreenBackground,
        topBar = {
            SquadifyTopBar(
                title = stringResource(Res.string.title_create_teams),
                subtitle = clubName,
                onNavigateBack = onNavigateBack
            )
        },
        bottomBar = {
            // Pinned, as on every form: the team rows scroll under it.
            val idle = uiState as? CreateTeamsUiState.Idle
            if (idle != null && idle.seasons.isNotEmpty()) {
                PinnedBottomBar {
                    idle.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    PrimaryLoadingButton(
                        text = Res.string.btn_create_teams,
                        submittingText = Res.string.btn_creating_teams,
                        isLoading = idle.isSubmitting,
                        onClick = onCreateClick
                    )
                }
            }
        }
    ) { paddingValues ->
        when (uiState) {
            is CreateTeamsUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues).background(ScreenBackground),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PitchGreen)
                }
            }

            is CreateTeamsUiState.Idle -> if (uiState.seasons.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues).background(ScreenBackground).screenContentPadding(),
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
                        .background(ScreenBackground)
                        .verticalScroll(rememberScrollState())
                        .readableWidth()
                        .screenContentPadding(),
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

                    // Under the age group, so adding a row never means scrolling past the others.
                    OutlinedButton(
                        onClick = onAddRow,
                        modifier = Modifier.fillMaxWidth(),
                        shape = FormRoundedShape,
                        border = BorderStroke(1.dp, PitchGreen.copy(alpha = 0.4f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PitchGreen)
                    ) {
                        Text("+ ${stringResource(Res.string.label_add_another_team)}")
                    }

                    rows.forEachIndexed { index, row ->
                        TeamRowCard(
                            index = index + 1,
                            row = row,
                            teamSizeOptions = teamSizeOptions,
                            isError = showValidationErrors && row.name.isBlank(),
                            canRemove = rows.size > 1,
                            onNameChanged = { onRowNameChanged(row.key, it) },
                            onTeamSizeSelected = { onRowTeamSizeSelected(row.key, it) },
                            onRemove = { onRemoveRow(row.key) },
                            onSubmit = onCreateClick.takeIf { index == rows.lastIndex }
                        )
                    }

                    if (anyBlankName) {
                        Text(
                            text = stringResource(Res.string.msg_team_name_required),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
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
    teamSizeOptions: List<Int>,
    isError: Boolean,
    canRemove: Boolean,
    onNameChanged: (String) -> Unit,
    onTeamSizeSelected: (Int) -> Unit,
    onRemove: () -> Unit,
    // Only on the last row: its keyboard key creates them all, like the button. Earlier rows' Next
    // moves on to the following row.
    onSubmit: (() -> Unit)? = null
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
                keyboardOptions = KeyboardOptions(imeAction = if (onSubmit != null) ImeAction.Done else ImeAction.Next),
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

            PickerField(
                label = stringResource(Res.string.label_team_size),
                selectedText = row.teamSize?.let { stringResource(Res.string.label_team_size_value, it) },
                placeholder = stringResource(Res.string.label_choose_team_size),
                options = teamSizeOptions.map {
                    it.toString() to stringResource(Res.string.label_team_size_value, it)
                },
                isError = false,
                onOptionSelected = { onTeamSizeSelected(it.toInt()) }
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
    startYear = 2015,
    endYear = 2015,
    teamSize = 7
)

@Preview
@Composable
private fun CreateTeamsContentPreview() {
    SquadifyTheme {
        CreateTeamsContent(
            uiState = CreateTeamsUiState.Idle(
                seasons = listOf(previewSeason("s1", "26/27"), previewSeason("s2", "25/26")),
                selectedSeasonId = "s1",
                ageGroups = listOf(previewAgeGroup("a1", "Sub 11", "s1"), previewAgeGroup("a2", "Sub 13", "s1")),
                selectedAgeGroupId = "a1"
            ),
            clubName = "CD Ribamar",
            rows = listOf(TeamRow(key = 0, name = "Team A", teamSize = 7), TeamRow(key = 1, teamSize = 7)),
            teamSizeOptions = listOf(4, 5, 7, 9, 11),
            showValidationErrors = false,
            onNavigateBack = {},
            onSeasonSelected = {},
            onAgeGroupSelected = {},
            onRowNameChanged = { _, _ -> },
            onRowTeamSizeSelected = { _, _ -> },
            onAddRow = {},
            onRemoveRow = {},
            onCreateClick = {}
        )
    }
}

@Preview
@Composable
private fun CreateTeamsContentValidationErrorsPreview() {
    SquadifyTheme(darkTheme = true) {
        CreateTeamsContent(
            uiState = CreateTeamsUiState.Idle(
                seasons = listOf(previewSeason("s1", "26/27")),
                selectedSeasonId = "s1",
                ageGroups = listOf(previewAgeGroup("a1", "Sub 11", "s1")),
                selectedAgeGroupId = null
            ),
            clubName = "CD Ribamar",
            rows = listOf(TeamRow(key = 0, name = "")),
            teamSizeOptions = listOf(4, 5, 7, 9, 11),
            showValidationErrors = true,
            onNavigateBack = {},
            onSeasonSelected = {},
            onAgeGroupSelected = {},
            onRowNameChanged = { _, _ -> },
            onRowTeamSizeSelected = { _, _ -> },
            onAddRow = {},
            onRemoveRow = {},
            onCreateClick = {}
        )
    }
}

@Preview(widthDp = 780, heightDp = 360)
@Composable
private fun CreateTeamsContentLandscapePreview() {
    SquadifyTheme {
        CreateTeamsContent(
            uiState = CreateTeamsUiState.Idle(
                seasons = listOf(previewSeason("s1", "26/27")),
                selectedSeasonId = "s1",
                ageGroups = listOf(previewAgeGroup("a1", "Sub 11", "s1")),
                selectedAgeGroupId = "a1",
                isSubmitting = true
            ),
            clubName = "CD Ribamar",
            rows = listOf(TeamRow(key = 0, name = "Team A", teamSize = 7)),
            teamSizeOptions = listOf(4, 5, 7, 9, 11),
            showValidationErrors = false,
            onNavigateBack = {},
            onSeasonSelected = {},
            onAgeGroupSelected = {},
            onRowNameChanged = { _, _ -> },
            onRowTeamSizeSelected = { _, _ -> },
            onAddRow = {},
            onRemoveRow = {},
            onCreateClick = {}
        )
    }
}
