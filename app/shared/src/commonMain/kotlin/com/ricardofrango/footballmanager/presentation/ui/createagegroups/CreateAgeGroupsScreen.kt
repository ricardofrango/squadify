package com.ricardofrango.footballmanager.presentation.ui.createagegroups

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ricardofrango.footballmanager.domain.AgeGroupDefaults
import com.ricardofrango.footballmanager.model.AgeGroupItemRequest
import com.ricardofrango.footballmanager.model.Season
import com.ricardofrango.footballmanager.model.SeasonType
import com.ricardofrango.footballmanager.presentation.ui.components.FMTopBar
import com.ricardofrango.footballmanager.presentation.ui.components.PrimaryLoadingButton
import com.ricardofrango.footballmanager.presentation.ui.theme.CarbonGray
import com.ricardofrango.footballmanager.presentation.ui.theme.DeepCarbon
import com.ricardofrango.footballmanager.presentation.ui.theme.FootballManagerTheme
import com.ricardofrango.footballmanager.presentation.ui.theme.PitchGreen
import footballmanager.app.shared.generated.resources.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock

/** One row of the batch. [key] is a stable identity for Compose iteration since rows can be removed
 * from the middle of the list (a plain index would shift and confuse item state). */
private data class AgeGroupRow(
    val key: Int,
    val birthYear: Int? = null,
    val name: String = ""
)

private val FormRoundedShape = RoundedCornerShape(18.dp)

/**
 * Batch age-group creation, matching `CreateAgeGroupsRequest` (a list of name+birthYear items under one
 * clubId/seasonId). Built to the "Create age groups" artboard of the design canvas linked in
 * AI_GUIDELINES.md ("UI Design Reference"): one card per row, a birth-year picker whose choice fills
 * the name field's placeholder (overridable), "+ Add another age group", and one CREATE that submits
 * every row in a single request -- CreateAgeGroupsUseCase already writes the whole batch inside one
 * `runInTransaction`, so either all rows are saved or none are.
 *
 * Age groups belong to exactly one season, so the screen opens with a season picker rather than
 * guessing: [initialSeasonId] pre-selects it when the caller already knows which season is meant (a
 * specific season's "no age groups" empty state), and is null from the overflow menu so the user
 * confirms the choice. Switching the picker re-scopes the birth-year exclusions and the inferred name.
 *
 * The birth-year picker excludes years already used by an existing age group in the selected season,
 * and years already picked by a sibling row in this same batch -- so neither duplicate class is
 * selectable in the first place (mirroring CreateSeasonScreen's date-range-constrained pickers),
 * rather than only being caught after a failed submit.
 *
 * Rows aren't `rememberSaveable`: preserving a list of a custom row type across process death needs a
 * custom Saver this project doesn't have yet, so a rare mid-edit process kill loses the in-progress
 * batch. Disclosed as a known simplification, not a silent gap.
 */
@Composable
fun CreateAgeGroupsScreen(
    clubId: String,
    clubName: String,
    initialSeasonId: String? = null,
    onNavigateBack: () -> Unit,
    onAgeGroupsCreated: () -> Unit,
    viewModel: CreateAgeGroupsViewModel = koinViewModel()
) {
    val uiState by viewModel.state.collectAsState()
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }

    var nextRowKey by remember { mutableStateOf(1) }
    val rows = remember { mutableStateListOf(AgeGroupRow(key = 0)) }

    // Start year of the currently picked season -- drives the inferred "Sub N" name. Null until a
    // season is selected (or when the club has none), in which case the name column stays disabled.
    val seasonStartYear = (uiState as? CreateAgeGroupsUiState.Idle)?.selectedSeason?.startDate?.year

    LaunchedEffect(clubId, initialSeasonId) {
        viewModel.load(clubId, initialSeasonId)
    }

    LaunchedEffect(viewModel.navigationEvent) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                CreateAgeGroupsNavigationEvent.NavigateBackWithAgeGroupsCreated -> onAgeGroupsCreated()
            }
        }
    }

    CreateAgeGroupsContent(
        uiState = uiState,
        clubName = clubName,
        today = today,
        rows = rows,
        onNavigateBack = onNavigateBack,
        onSeasonSelected = { seasonId ->
            viewModel.onSeasonSelected(seasonId)
            // A birth year valid in the old season may already be taken in the new one -- drop the
            // now-possibly-conflicting picks rather than carrying them silently into a failed submit.
            for (i in rows.indices) {
                rows[i] = rows[i].copy(birthYear = null, name = "")
            }
        },
        onRowBirthYearSelected = { rowKey, birthYear ->
            val index = rows.indexOfFirst { it.key == rowKey }
            if (index >= 0) {
                val row = rows[index]
                // Only replace the name if it was still the placeholder-driven inferred value (or
                // blank) -- a name the user typed themselves is never silently overwritten.
                val previousInferred = row.birthYear?.let { year ->
                    seasonStartYear?.let { AgeGroupDefaults.inferredName(year, it) }
                }
                val keepName = row.name.isNotBlank() && row.name != previousInferred
                rows[index] = row.copy(
                    birthYear = birthYear,
                    name = if (keepName) row.name else ""
                )
            }
            viewModel.clearSubmitError()
        },
        onRowNameChanged = { rowKey, name ->
            val index = rows.indexOfFirst { it.key == rowKey }
            if (index >= 0) rows[index] = rows[index].copy(name = name)
            viewModel.clearSubmitError()
        },
        onAddRow = {
            rows.add(AgeGroupRow(key = nextRowKey))
            nextRowKey += 1
        },
        onRemoveRow = { rowKey ->
            rows.removeAll { it.key == rowKey }
        },
        onCreateClick = onCreateClick@{
            val startYear = seasonStartYear ?: return@onCreateClick
            val items = rows.mapNotNull { row ->
                val birthYear = row.birthYear ?: return@mapNotNull null
                AgeGroupItemRequest(
                    name = row.name.ifBlank { AgeGroupDefaults.inferredName(birthYear, startYear) },
                    birthYear = birthYear
                )
            }
            viewModel.onCreateClicked(clubId, items)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateAgeGroupsContent(
    uiState: CreateAgeGroupsUiState,
    clubName: String,
    today: LocalDate,
    rows: List<AgeGroupRow>,
    onNavigateBack: () -> Unit,
    onSeasonSelected: (seasonId: String) -> Unit,
    onRowBirthYearSelected: (rowKey: Int, birthYear: Int) -> Unit,
    onRowNameChanged: (rowKey: Int, name: String) -> Unit,
    onAddRow: () -> Unit,
    onRemoveRow: (rowKey: Int) -> Unit,
    onCreateClick: () -> Unit
) {
    val selectedSeasonName = (uiState as? CreateAgeGroupsUiState.Idle)?.selectedSeason?.name

    Scaffold(
        containerColor = Color.White,
        topBar = {
            FMTopBar(
                title = stringResource(Res.string.title_create_age_groups),
                subtitle = if (selectedSeasonName != null) {
                    stringResource(Res.string.label_age_groups_header_subtitle, clubName, selectedSeasonName)
                } else {
                    clubName
                },
                onNavigateBack = onNavigateBack
            )
        }
    ) { paddingValues ->
        when (uiState) {
            is CreateAgeGroupsUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues).background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PitchGreen)
                }
            }

            is CreateAgeGroupsUiState.Idle -> if (uiState.seasons.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues).background(Color.White).padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(Res.string.msg_no_seasons_for_age_groups),
                        style = MaterialTheme.typography.bodyMedium,
                        color = DeepCarbon.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                val seasonStartYear = uiState.selectedSeason?.startDate?.year
                val allYearOptions = remember(today) { AgeGroupDefaults.birthYearOptions(today) }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .background(Color.White)
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SeasonField(
                        label = stringResource(Res.string.label_season_field),
                        seasons = uiState.seasons,
                        selectedSeasonId = uiState.selectedSeasonId,
                        onSeasonSelected = onSeasonSelected
                    )

                    Text(
                        text = stringResource(Res.string.msg_age_groups_intro),
                        style = MaterialTheme.typography.bodySmall,
                        color = DeepCarbon.copy(alpha = 0.6f)
                    )

                    rows.forEachIndexed { index, row ->
                        val pickedBySiblingRows = rows.filter { it.key != row.key }.mapNotNull { it.birthYear }.toSet()
                        val availableYears = allYearOptions - uiState.usedBirthYears - pickedBySiblingRows
                        AgeGroupRowCard(
                            index = index + 1,
                            row = row,
                            availableYears = availableYears,
                            inferredName = row.birthYear?.let { year ->
                                seasonStartYear?.let { AgeGroupDefaults.inferredName(year, it) }
                            },
                            canRemove = rows.size > 1,
                            onBirthYearSelected = { onRowBirthYearSelected(row.key, it) },
                            onNameChanged = { onRowNameChanged(row.key, it) },
                            onRemove = { onRemoveRow(row.key) }
                        )
                    }

                    OutlinedButton(
                        onClick = onAddRow,
                        modifier = Modifier.fillMaxWidth(),
                        shape = FormRoundedShape,
                        border = BorderStroke(1.dp, PitchGreen.copy(alpha = 0.4f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PitchGreen)
                    ) {
                        Text("+ ${stringResource(Res.string.label_add_another_age_group)}")
                    }

                    if (uiState.error != null) {
                        Text(
                            text = uiState.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    PrimaryLoadingButton(
                        text = Res.string.btn_create_age_group,
                        submittingText = Res.string.btn_creating_age_groups,
                        isLoading = uiState.isSubmitting,
                        enabled = uiState.selectedSeasonId != null &&
                                rows.isNotEmpty() && rows.all { it.birthYear != null },
                        onClick = onCreateClick
                    )

                    Text(
                        text = stringResource(Res.string.msg_age_groups_footer_helper),
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
private fun SeasonField(
    label: String,
    seasons: List<Season>,
    selectedSeasonId: String?,
    onSeasonSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = seasons.find { it.id == selectedSeasonId }?.name
    Column {
        Text(
            text = label,
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
                        text = selectedName ?: stringResource(Res.string.label_choose_season),
                        color = if (selectedName != null) DeepCarbon else DeepCarbon.copy(alpha = 0.4f),
                        fontWeight = if (selectedName != null) FontWeight.Bold else FontWeight.Normal
                    )
                    Text("▾", color = DeepCarbon.copy(alpha = 0.4f), fontWeight = FontWeight.Bold)
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color.White)
            ) {
                seasons.forEach { season ->
                    DropdownMenuItem(
                        text = { Text(season.name, color = DeepCarbon) },
                        onClick = {
                            onSeasonSelected(season.id)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AgeGroupRowCard(
    index: Int,
    row: AgeGroupRow,
    availableYears: List<Int>,
    inferredName: String?,
    canRemove: Boolean,
    onBirthYearSelected: (Int) -> Unit,
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
                    text = stringResource(Res.string.label_age_group_section).uppercase(),
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

            YearOfBirthField(
                label = stringResource(Res.string.label_year_of_birth),
                selectedYear = row.birthYear,
                availableYears = availableYears,
                onYearSelected = onBirthYearSelected
            )

            OutlinedTextField(
                value = row.name,
                onValueChange = onNameChanged,
                label = { Text(stringResource(Res.string.label_age_group_name_field)) },
                placeholder = {
                    Text(
                        if (row.birthYear != null) {
                            inferredName.orEmpty()
                        } else {
                            stringResource(Res.string.label_name_placeholder_pick_year_first)
                        }
                    )
                },
                enabled = row.birthYear != null,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = FormRoundedShape,
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

@Composable
private fun YearOfBirthField(
    label: String,
    selectedYear: Int?,
    availableYears: List<Int>,
    onYearSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(
            text = label,
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
                        text = selectedYear?.toString() ?: stringResource(Res.string.label_choose_year),
                        color = if (selectedYear != null) DeepCarbon else DeepCarbon.copy(alpha = 0.4f),
                        fontWeight = if (selectedYear != null) FontWeight.Bold else FontWeight.Normal
                    )
                    Text("▾", color = DeepCarbon.copy(alpha = 0.4f), fontWeight = FontWeight.Bold)
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color.White)
            ) {
                availableYears.forEach { year ->
                    DropdownMenuItem(
                        text = { Text(year.toString(), color = DeepCarbon) },
                        onClick = {
                            onYearSelected(year)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

private fun previewSeason(id: String, name: String, startYear: Int) = Season(
    id = id,
    clubId = "club-1",
    name = name,
    type = SeasonType.EUROPEAN_YEAR,
    startDate = LocalDate(startYear, 7, 1),
    endDate = LocalDate(startYear + 1, 6, 30),
    active = true
)

@Preview
@Composable
private fun CreateAgeGroupsContentPreview() {
    val today = remember { LocalDate(2026, 9, 4) }
    FootballManagerTheme {
        CreateAgeGroupsContent(
            uiState = CreateAgeGroupsUiState.Idle(
                seasons = listOf(previewSeason("s1", "26/27", 2026), previewSeason("s2", "25/26", 2025)),
                selectedSeasonId = "s1"
            ),
            clubName = "CD Ribamar",
            today = today,
            rows = listOf(AgeGroupRow(key = 0, birthYear = 2015), AgeGroupRow(key = 1)),
            onNavigateBack = {},
            onSeasonSelected = {},
            onRowBirthYearSelected = { _, _ -> },
            onRowNameChanged = { _, _ -> },
            onAddRow = {},
            onRemoveRow = {},
            onCreateClick = {}
        )
    }
}

@Preview
@Composable
private fun CreateAgeGroupsContentSubmittingPreview() {
    val today = remember { LocalDate(2026, 9, 4) }
    FootballManagerTheme {
        CreateAgeGroupsContent(
            uiState = CreateAgeGroupsUiState.Idle(
                seasons = listOf(previewSeason("s1", "26/27", 2026)),
                selectedSeasonId = "s1",
                isSubmitting = true
            ),
            clubName = "CD Ribamar",
            today = today,
            rows = listOf(AgeGroupRow(key = 0, birthYear = 2015)),
            onNavigateBack = {},
            onSeasonSelected = {},
            onRowBirthYearSelected = { _, _ -> },
            onRowNameChanged = { _, _ -> },
            onAddRow = {},
            onRemoveRow = {},
            onCreateClick = {}
        )
    }
}
