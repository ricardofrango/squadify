package com.ricardofrango.squadify.presentation.ui.createagegroups

import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.ricardofrango.squadify.presentation.ui.components.submitKeyboardActions
import com.ricardofrango.squadify.presentation.navigation.collectSettledState
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
import com.ricardofrango.squadify.domain.AgeGroupDefaults
import com.ricardofrango.squadify.model.AgeGroupItemRequest
import com.ricardofrango.squadify.model.Season
import com.ricardofrango.squadify.model.SeasonType
import com.ricardofrango.squadify.model.SportType
import com.ricardofrango.squadify.presentation.ui.components.SquadifyTopBar
import com.ricardofrango.squadify.presentation.ui.components.PrimaryLoadingButton
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyClubTheme
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.screenContentPadding
import squadify.app.shared.generated.resources.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock

/** Whether a row covers a single birth year (most football age groups) or a two-year span (typical
 * for futsal, which needs two birth years' worth of kids to field a squad). */
internal enum class AgeGroupSpanType { SINGLE_YEAR, TWO_YEAR }

/** One row of the batch. [key] is a stable identity for Compose iteration since rows can be removed
 * from the middle of the list (a plain index would shift and confuse item state).
 *
 * [startYear]/[endYear] mirror `AgeGroupItemRequest`: for [AgeGroupSpanType.SINGLE_YEAR] both are set
 * to the one picked year; for [AgeGroupSpanType.TWO_YEAR] they're picked independently, startYear being
 * the younger (more recent) birth year and endYear the older one -- eligibility and the inferred "Sub
 * N" name are always derived from endYear, same as the old single-birth-year field. */
private data class AgeGroupRow(
    val key: Int,
    val spanType: AgeGroupSpanType = AgeGroupSpanType.SINGLE_YEAR,
    val startYear: Int? = null,
    val endYear: Int? = null,
    val name: String = "",
    // The a-side count this age group's teams play by default. Seeded from the club sport's largest
    // format and overridable per row; sent as `AgeGroupItemRequest.teamSize`.
    val teamSize: Int? = null
)

private val FormRoundedShape = RoundedCornerShape(18.dp)

/**
 * Batch age-group creation, matching `CreateAgeGroupsRequest` (a list of name+startYear+endYear items
 * under one clubId/seasonId). Built to the "Create age groups" artboard of the design canvas linked in
 * AI_GUIDELINES.md ("UI Design Reference"): one card per row, a span-type radio (single birth year, the
 * football norm, vs. a two-year span for e.g. futsal groups that need two cohorts to field a squad), a
 * year picker (or two) whose choice fills the name field's placeholder (overridable), "+ Add another age
 * group", and one CREATE that submits every row in a single request -- CreateAgeGroupsUseCase already
 * writes the whole batch inside one `runInTransaction`, so either all rows are saved or none are.
 *
 * Age groups belong to exactly one season, so the screen opens with a season picker rather than
 * guessing: [initialSeasonId] pre-selects it when the caller already knows which season is meant (a
 * specific season's "no age groups" empty state), and is null from the overflow menu so the user
 * confirms the choice. Switching the picker re-scopes the birth-year exclusions and the inferred name.
 *
 * For single-year rows, the year picker excludes years already used by an existing age group in the
 * selected season, and years already picked by a sibling row in this same batch -- so neither duplicate
 * class is selectable in the first place (mirroring CreateSeasonScreen's date-range-constrained
 * pickers). Two-year rows are free-form (each of the two pickers offers the full year range); an
 * inverted or duplicate range is still caught before submit / by the server.
 *
 * Rows aren't `rememberSaveable`: preserving a list of a custom row type across process death needs a
 * custom Saver this project doesn't have yet, so a rare mid-edit process kill loses the in-progress
 * batch. Disclosed as a known simplification, not a silent gap.
 */
@Composable
fun CreateAgeGroupsScreen(
    clubId: String,
    clubName: String,
    // The club's sport -- its `teamSizes` are the options for the per-row team-size picker.
    sport: SportType,
    initialSeasonId: String? = null,
    onNavigateBack: () -> Unit,
    onAgeGroupsCreated: () -> Unit,
    viewModel: CreateAgeGroupsViewModel = koinViewModel()
) {
    SquadifyClubTheme {
        val uiState = viewModel.state.collectSettledState()
        val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }

        // Default a new age group to the sport's largest format (e.g. 11-a-side football); the coordinator
        // narrows it for the younger groups.
        val defaultTeamSize = sport.teamSizes.last()

        var nextRowKey by remember { mutableStateOf(1) }
        val rows = remember { mutableStateListOf(AgeGroupRow(key = 0, teamSize = defaultTeamSize)) }

        // End year of the currently picked season -- drives the inferred "Sub N" name (age reached within
        // the season). Null until a season is selected, in which case the name column stays disabled.
        val seasonEndYear = (uiState as? CreateAgeGroupsUiState.Idle)?.selectedSeason?.endDate?.year

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
            teamSizeOptions = sport.teamSizes,
            onNavigateBack = onNavigateBack,
            onSeasonSelected = { seasonId ->
                viewModel.onSeasonSelected(seasonId)
                // A birth year valid in the old season may already be taken in the new one -- drop the
                // now-possibly-conflicting picks rather than carrying them silently into a failed submit.
                for (i in rows.indices) {
                    rows[i] = rows[i].copy(startYear = null, endYear = null, name = "")
                }
            },
            onRowSpanTypeSelected = { rowKey, spanType ->
                val index = rows.indexOfFirst { it.key == rowKey }
                if (index >= 0) {
                    val row = rows[index]
                    rows[index] = when (spanType) {
                        // Collapsing to a single year: keep the older/end year as the one picked year.
                        AgeGroupSpanType.SINGLE_YEAR -> row.copy(spanType = spanType, startYear = row.endYear)
                        AgeGroupSpanType.TWO_YEAR -> row.copy(spanType = spanType)
                    }
                }
                viewModel.clearSubmitError()
            },
            onRowStartYearSelected = { rowKey, startYear ->
                val index = rows.indexOfFirst { it.key == rowKey }
                if (index >= 0) rows[index] = rows[index].copy(startYear = startYear)
                viewModel.clearSubmitError()
            },
            onRowEndYearSelected = { rowKey, endYear ->
                val index = rows.indexOfFirst { it.key == rowKey }
                if (index >= 0) {
                    val row = rows[index]
                    // Fill the name with the inferred "Sub N" as real, editable text. A name the user
                    // typed themselves is kept; one that's still the previous year's inferred value (or
                    // blank) is replaced with the new year's. Single-year rows also pin startYear == endYear.
                    val previousInferred = row.endYear?.let { year ->
                        seasonEndYear?.let { AgeGroupDefaults.inferredName(year, it) }
                    }
                    val newInferred = seasonEndYear?.let { AgeGroupDefaults.inferredName(endYear, it) }.orEmpty()
                    val keepName = row.name.isNotBlank() && row.name != previousInferred
                    rows[index] = row.copy(
                        endYear = endYear,
                        startYear = if (row.spanType == AgeGroupSpanType.SINGLE_YEAR) endYear else row.startYear,
                        name = if (keepName) row.name else newInferred
                    )
                }
                viewModel.clearSubmitError()
            },
            onRowNameChanged = { rowKey, name ->
                val index = rows.indexOfFirst { it.key == rowKey }
                if (index >= 0) rows[index] = rows[index].copy(name = name)
                viewModel.clearSubmitError()
            },
            onRowTeamSizeSelected = { rowKey, teamSize ->
                val index = rows.indexOfFirst { it.key == rowKey }
                if (index >= 0) rows[index] = rows[index].copy(teamSize = teamSize)
                viewModel.clearSubmitError()
            },
            onAddRow = {
                rows.add(AgeGroupRow(key = nextRowKey, teamSize = defaultTeamSize))
                nextRowKey += 1
            },
            onRemoveRow = { rowKey ->
                rows.removeAll { it.key == rowKey }
            },
            onCreateClick = onCreateClick@{
                val seasonEnd = seasonEndYear ?: return@onCreateClick
                val items = rows.mapNotNull { row ->
                    val endYear = row.endYear ?: return@mapNotNull null
                    val startYear = row.startYear ?: return@mapNotNull null
                    AgeGroupItemRequest(
                        name = row.name.ifBlank { AgeGroupDefaults.inferredName(endYear, seasonEnd) },
                        startYear = startYear,
                        endYear = endYear,
                        teamSize = row.teamSize
                    )
                }
                viewModel.onCreateClicked(clubId, items)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateAgeGroupsContent(
    uiState: CreateAgeGroupsUiState,
    clubName: String,
    today: LocalDate,
    rows: List<AgeGroupRow>,
    teamSizeOptions: List<Int>,
    onNavigateBack: () -> Unit,
    onSeasonSelected: (seasonId: String) -> Unit,
    onRowSpanTypeSelected: (rowKey: Int, spanType: AgeGroupSpanType) -> Unit,
    onRowStartYearSelected: (rowKey: Int, startYear: Int) -> Unit,
    onRowEndYearSelected: (rowKey: Int, endYear: Int) -> Unit,
    onRowNameChanged: (rowKey: Int, name: String) -> Unit,
    onRowTeamSizeSelected: (rowKey: Int, teamSize: Int) -> Unit,
    onAddRow: () -> Unit,
    onRemoveRow: (rowKey: Int) -> Unit,
    onCreateClick: () -> Unit
) {
    val selectedSeasonName = (uiState as? CreateAgeGroupsUiState.Idle)?.selectedSeason?.name

    Scaffold(
        containerColor = Color.White,
        topBar = {
            SquadifyTopBar(
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
                    modifier = Modifier.fillMaxSize().padding(paddingValues).background(Color.White).screenContentPadding(),
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
                val seasonEndYear = uiState.selectedSeason?.endDate?.year
                val allYearOptions = remember(today) { AgeGroupDefaults.birthYearOptions(today) }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .background(Color.White)
                        .verticalScroll(rememberScrollState())
                        .screenContentPadding(),
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
                        // Single-year rows still exclude years already used/picked elsewhere; a
                        // two-year row's two pickers stay unrestricted (each is a distinct field, and
                        // overlap across rows is a coordinator judgment call, not something worth
                        // second-guessing with a picker exclusion here).
                        val pickedBySiblingRows = rows.filter { it.key != row.key }
                            .filter { it.spanType == AgeGroupSpanType.SINGLE_YEAR }
                            .mapNotNull { it.endYear }.toSet()
                        val availableYears = allYearOptions - uiState.usedBirthYears - pickedBySiblingRows
                        AgeGroupRowCard(
                            index = index + 1,
                            row = row,
                            conflict = index in uiState.conflictRows,
                            availableYears = availableYears,
                            allYearOptions = allYearOptions,
                            teamSizeOptions = teamSizeOptions,
                            inferredName = row.endYear?.let { year ->
                                seasonEndYear?.let { AgeGroupDefaults.inferredName(year, it) }
                            },
                            canRemove = rows.size > 1,
                            onSpanTypeSelected = { onRowSpanTypeSelected(row.key, it) },
                            onStartYearSelected = { onRowStartYearSelected(row.key, it) },
                            onEndYearSelected = { onRowEndYearSelected(row.key, it) },
                            onNameChanged = { onRowNameChanged(row.key, it) },
                            onTeamSizeSelected = { onRowTeamSizeSelected(row.key, it) },
                            onRemove = { onRemoveRow(row.key) },
                            onSubmit = onCreateClick.takeIf { index == rows.lastIndex }
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
                                rows.isNotEmpty() &&
                                rows.all { it.startYear != null && it.endYear != null && it.startYear >= it.endYear },
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
    // The server said this row's birth years clash with another age group: red border and number.
    conflict: Boolean = false,
    availableYears: List<Int>,
    allYearOptions: List<Int>,
    teamSizeOptions: List<Int>,
    inferredName: String?,
    canRemove: Boolean,
    onSpanTypeSelected: (AgeGroupSpanType) -> Unit,
    onStartYearSelected: (Int) -> Unit,
    onEndYearSelected: (Int) -> Unit,
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
        border = if (conflict) BorderStroke(2.dp, MaterialTheme.colorScheme.error) else BorderStroke(1.dp, CarbonGray.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(24.dp).background(if (conflict) MaterialTheme.colorScheme.error else CarbonGray.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = index.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (conflict) Color.White else DeepCarbon
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

            AgeGroupSpanTypeField(
                label = stringResource(Res.string.label_age_group_span_type),
                selectedType = row.spanType,
                onTypeSelected = onSpanTypeSelected
            )

            if (row.spanType == AgeGroupSpanType.SINGLE_YEAR) {
                YearOfBirthField(
                    label = stringResource(Res.string.label_year_of_birth),
                    selectedYear = row.endYear,
                    availableYears = availableYears,
                    onYearSelected = onEndYearSelected
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        YearOfBirthField(
                            label = stringResource(Res.string.label_year_of_birth_start),
                            selectedYear = row.startYear,
                            availableYears = allYearOptions,
                            onYearSelected = onStartYearSelected
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        YearOfBirthField(
                            label = stringResource(Res.string.label_year_of_birth_end),
                            selectedYear = row.endYear,
                            availableYears = allYearOptions,
                            onYearSelected = onEndYearSelected
                        )
                    }
                }
                if (row.startYear != null && row.endYear != null && row.startYear < row.endYear) {
                    Text(
                        text = stringResource(Res.string.msg_age_group_range_inverted),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            OutlinedTextField(
                value = row.name,
                onValueChange = onNameChanged,
                label = { Text(stringResource(Res.string.label_age_group_name_field)) },
                placeholder = {
                    Text(
                        if (row.endYear != null) {
                            inferredName.orEmpty()
                        } else {
                            stringResource(Res.string.label_name_placeholder_pick_year_first)
                        }
                    )
                },
                enabled = row.endYear != null,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = if (onSubmit != null) ImeAction.Done else ImeAction.Next),
                keyboardActions = onSubmit?.let { submitKeyboardActions(it) } ?: KeyboardActions.Default,
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

            TeamSizeField(
                label = stringResource(Res.string.label_team_size),
                selectedTeamSize = row.teamSize,
                options = teamSizeOptions,
                onTeamSizeSelected = onTeamSizeSelected
            )
        }
    }
}

@Composable
internal fun TeamSizeField(
    label: String,
    selectedTeamSize: Int?,
    options: List<Int>,
    onTeamSizeSelected: (Int) -> Unit
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
                        text = selectedTeamSize?.let {
                            stringResource(Res.string.label_team_size_value, it)
                        } ?: stringResource(Res.string.label_choose_team_size),
                        color = if (selectedTeamSize != null) DeepCarbon else DeepCarbon.copy(alpha = 0.4f),
                        fontWeight = if (selectedTeamSize != null) FontWeight.Bold else FontWeight.Normal
                    )
                    Text("▾", color = DeepCarbon.copy(alpha = 0.4f), fontWeight = FontWeight.Bold)
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color.White)
            ) {
                options.forEach { size ->
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(Res.string.label_team_size_value, size), color = DeepCarbon)
                        },
                        onClick = {
                            onTeamSizeSelected(size)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun AgeGroupSpanTypeField(
    label: String,
    selectedType: AgeGroupSpanType,
    onTypeSelected: (AgeGroupSpanType) -> Unit
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = DeepCarbon.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AgeGroupSpanType.entries.forEachIndexed { index, type ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onTypeSelected(type) }
                ) {
                    RadioButton(
                        selected = selectedType == type,
                        onClick = { onTypeSelected(type) },
                        colors = RadioButtonDefaults.colors(selectedColor = PitchGreen)
                    )
                    Text(
                        text = stringResource(
                            if (type == AgeGroupSpanType.SINGLE_YEAR) {
                                Res.string.label_age_group_span_single
                            } else {
                                Res.string.label_age_group_span_range
                            }
                        ),
                        color = DeepCarbon
                    )
                }
                if (index == 0) Spacer(modifier = Modifier.width(8.dp))
            }
        }
    }
}

@Composable
internal fun YearOfBirthField(
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
    SquadifyTheme {
        CreateAgeGroupsContent(
            uiState = CreateAgeGroupsUiState.Idle(
                seasons = listOf(previewSeason("s1", "26/27", 2026), previewSeason("s2", "25/26", 2025)),
                selectedSeasonId = "s1"
            ),
            clubName = "CD Ribamar",
            today = today,
            rows = listOf(
                AgeGroupRow(key = 0, startYear = 2015, endYear = 2015, teamSize = 11),
                AgeGroupRow(
                    key = 1, spanType = AgeGroupSpanType.TWO_YEAR,
                    startYear = 2017, endYear = 2016, teamSize = 5
                )
            ),
            teamSizeOptions = listOf(4, 5, 7, 9, 11),
            onNavigateBack = {},
            onSeasonSelected = {},
            onRowSpanTypeSelected = { _, _ -> },
            onRowStartYearSelected = { _, _ -> },
            onRowEndYearSelected = { _, _ -> },
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
private fun CreateAgeGroupsContentSubmittingPreview() {
    val today = remember { LocalDate(2026, 9, 4) }
    SquadifyTheme {
        CreateAgeGroupsContent(
            uiState = CreateAgeGroupsUiState.Idle(
                seasons = listOf(previewSeason("s1", "26/27", 2026)),
                selectedSeasonId = "s1",
                isSubmitting = true
            ),
            clubName = "CD Ribamar",
            today = today,
            rows = listOf(AgeGroupRow(key = 0, startYear = 2015, endYear = 2015, teamSize = 11)),
            teamSizeOptions = listOf(4, 5, 7, 9, 11),
            onNavigateBack = {},
            onSeasonSelected = {},
            onRowSpanTypeSelected = { _, _ -> },
            onRowStartYearSelected = { _, _ -> },
            onRowEndYearSelected = { _, _ -> },
            onRowNameChanged = { _, _ -> },
            onRowTeamSizeSelected = { _, _ -> },
            onAddRow = {},
            onRemoveRow = {},
            onCreateClick = {}
        )
    }
}
