package com.ricardofrango.squadify.presentation.ui.createseason

import com.ricardofrango.squadify.presentation.ui.components.submitKeyboardActions
import com.ricardofrango.squadify.presentation.navigation.collectSettledState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.domain.SeasonDateDefaults
import com.ricardofrango.squadify.model.SeasonType
import com.ricardofrango.squadify.presentation.ui.components.PrimaryLoadingButton
import com.ricardofrango.squadify.presentation.ui.components.DatePickerSheet
import com.ricardofrango.squadify.presentation.ui.components.SquadifyTopBar
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyClubTheme
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.btn_create_season
import squadify.app.shared.generated.resources.btn_creating_season
import squadify.app.shared.generated.resources.ic_back
import squadify.app.shared.generated.resources.label_back
import squadify.app.shared.generated.resources.label_dates_section
import squadify.app.shared.generated.resources.label_days_count
import squadify.app.shared.generated.resources.label_ends
import squadify.app.shared.generated.resources.label_name_section
import squadify.app.shared.generated.resources.label_optional
import squadify.app.shared.generated.resources.label_season_name_field
import squadify.app.shared.generated.resources.label_season_name_placeholder
import squadify.app.shared.generated.resources.label_season_type
import squadify.app.shared.generated.resources.label_season_type_calendar
import squadify.app.shared.generated.resources.label_season_type_calendar_range
import squadify.app.shared.generated.resources.label_season_type_european
import squadify.app.shared.generated.resources.label_season_type_european_range
import squadify.app.shared.generated.resources.label_select_end_date
import squadify.app.shared.generated.resources.label_select_start_date
import squadify.app.shared.generated.resources.label_starts
import squadify.app.shared.generated.resources.msg_create_season_conflict
import squadify.app.shared.generated.resources.msg_create_season_conflict_unknown
import squadify.app.shared.generated.resources.msg_season_name_helper
import squadify.app.shared.generated.resources.msg_season_type_helper_calendar
import squadify.app.shared.generated.resources.msg_season_type_helper_european
import squadify.app.shared.generated.resources.title_create_season
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock

private val DateDisplayFormat = LocalDate.Format {
    day()
    char(' ')
    monthName(MonthNames.ENGLISH_ABBREVIATED)
    char(' ')
    year()
}

private val FormRoundedShape = RoundedCornerShape(18.dp)

/**
 * Season creation form matching `CreateSeasonRequest` (minus `clubId`, inferred from the open club).
 * Built to the "Create a season" artboard of the design canvas linked in AI_GUIDELINES.md
 * ("UI Design Reference"): type is the primary control -- picking one always overwrites the date
 * fields with that type's default range (even over a manual edit), because the type determines both
 * the date rule and the inferred name; the name field stays last and optional, previewing the
 * inferred value as its placeholder rather than pre-filling it.
 *
 * Owns all form state (this is where it needs to live -- ViewModel only tracks submit/load status);
 * rendering itself is delegated to the stateless [CreateSeasonContent] so it can be exercised from
 * `@Preview` without a Koin-backed ViewModel.
 */
@Composable
fun CreateSeasonScreen(
    clubId: String,
    clubName: String,
    onNavigateBack: () -> Unit,
    onSeasonCreated: () -> Unit,
    viewModel: CreateSeasonViewModel = koinViewModel()
) {
    SquadifyClubTheme {
        val uiState = viewModel.state.collectSettledState()
        val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }

        var selectedTypeName by rememberSaveable { mutableStateOf(SeasonType.EUROPEAN_YEAR.name) }
        val selectedType = SeasonType.valueOf(selectedTypeName)

        val initialDates = remember { SeasonDateDefaults.datesFor(SeasonType.EUROPEAN_YEAR, today) }
        var startDateIso by rememberSaveable { mutableStateOf(initialDates.first.toString()) }
        var endDateIso by rememberSaveable { mutableStateOf(initialDates.second.toString()) }
        val startDate = LocalDate.parse(startDateIso)
        val endDate = LocalDate.parse(endDateIso)

        var name by rememberSaveable { mutableStateOf("") }
        var showStartPicker by rememberSaveable { mutableStateOf(false) }
        var showEndPicker by rememberSaveable { mutableStateOf(false) }

        LaunchedEffect(clubId) {
            viewModel.loadExistingSeasons(clubId)
        }

        LaunchedEffect(viewModel.navigationEvent) {
            viewModel.navigationEvent.collect { event ->
                when (event) {
                    CreateSeasonNavigationEvent.NavigateBackWithSeasonCreated -> onSeasonCreated()
                }
            }
        }

        CreateSeasonContent(
            uiState = uiState,
            clubName = clubName,
            today = today,
            selectedType = selectedType,
            startDate = startDate,
            endDate = endDate,
            name = name,
            showStartPicker = showStartPicker,
            showEndPicker = showEndPicker,
            onNavigateBack = onNavigateBack,
            onTypeSelected = { type ->
                selectedTypeName = type.name
                val (start, end) = SeasonDateDefaults.datesFor(type, today)
                startDateIso = start.toString()
                endDateIso = end.toString()
                viewModel.clearSubmitError()
            },
            onNameChange = {
                name = it
                viewModel.clearSubmitError()
            },
            onStartDateClick = { showStartPicker = true },
            onEndDateClick = { showEndPicker = true },
            onStartDateConfirmed = {
                startDateIso = it.toString()
                showStartPicker = false
                viewModel.clearSubmitError()
            },
            onEndDateConfirmed = {
                endDateIso = it.toString()
                showEndPicker = false
                viewModel.clearSubmitError()
            },
            onDismissStartPicker = { showStartPicker = false },
            onDismissEndPicker = { showEndPicker = false },
            onCreateClick = {
                viewModel.onCreateClicked(
                    clubId = clubId,
                    type = selectedType,
                    startDate = startDate,
                    endDate = endDate,
                    name = name
                )
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateSeasonContent(
    uiState: CreateSeasonUiState,
    clubName: String,
    today: LocalDate,
    selectedType: SeasonType,
    startDate: LocalDate,
    endDate: LocalDate,
    name: String,
    showStartPicker: Boolean,
    showEndPicker: Boolean,
    onNavigateBack: () -> Unit,
    onTypeSelected: (SeasonType) -> Unit,
    onNameChange: (String) -> Unit,
    onStartDateClick: () -> Unit,
    onEndDateClick: () -> Unit,
    onStartDateConfirmed: (LocalDate) -> Unit,
    onEndDateConfirmed: (LocalDate) -> Unit,
    onDismissStartPicker: () -> Unit,
    onDismissEndPicker: () -> Unit,
    onCreateClick: () -> Unit
) {
    val inferredName = SeasonDateDefaults.inferredName(selectedType, startDate, endDate)
    val europeanDefaults =
        remember(today) { SeasonDateDefaults.datesFor(SeasonType.EUROPEAN_YEAR, today) }
    val calendarDefaults =
        remember(today) { SeasonDateDefaults.datesFor(SeasonType.CALENDAR_YEAR, today) }
    val europeanPill = SeasonDateDefaults.inferredName(
        SeasonType.EUROPEAN_YEAR,
        europeanDefaults.first,
        europeanDefaults.second
    )
    val calendarPill = SeasonDateDefaults.inferredName(
        SeasonType.CALENDAR_YEAR,
        calendarDefaults.first,
        calendarDefaults.second
    )

    Scaffold(
        // Explicit white, not MaterialTheme.colorScheme.background: every screen in this app renders
        // light content regardless of system theme (only the header bars are dark) -- same convention
        // ClubDetailsScreen etc. follow. Without this, a device in dark mode gets SquadifyTheme's
        // DeepCarbon background here instead.
        containerColor = Color.White,
        topBar = {
            SquadifyTopBar(
                title = stringResource(Res.string.title_create_season),
                subtitle = clubName,
                onNavigateBack = onNavigateBack
            )
        }
    ) { paddingValues ->
        when (uiState) {
            is CreateSeasonUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PitchGreen)
                }
            }

            is CreateSeasonUiState.Idle -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .background(Color.White)
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SectionLabel(stringResource(Res.string.label_season_type))

                    SeasonTypeRow(
                        title = stringResource(Res.string.label_season_type_european),
                        rangeLabel = stringResource(Res.string.label_season_type_european_range),
                        valuePill = europeanPill,
                        selected = selectedType == SeasonType.EUROPEAN_YEAR,
                        onClick = { onTypeSelected(SeasonType.EUROPEAN_YEAR) }
                    )
                    SeasonTypeRow(
                        title = stringResource(Res.string.label_season_type_calendar),
                        rangeLabel = stringResource(Res.string.label_season_type_calendar_range),
                        valuePill = calendarPill,
                        selected = selectedType == SeasonType.CALENDAR_YEAR,
                        onClick = { onTypeSelected(SeasonType.CALENDAR_YEAR) }
                    )

                    Text(
                        text = stringResource(
                            if (selectedType == SeasonType.EUROPEAN_YEAR) {
                                Res.string.msg_season_type_helper_european
                            } else {
                                Res.string.msg_season_type_helper_calendar
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = DeepCarbon.copy(alpha = 0.6f)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionLabel(stringResource(Res.string.label_dates_section))
                        Text(
                            text = stringResource(
                                Res.string.label_days_count,
                                (startDate.daysUntil(endDate) + 1).toString()
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = DeepCarbon.copy(alpha = 0.5f)
                        )
                    }

                    DateRow(
                        label = stringResource(Res.string.label_starts),
                        date = startDate,
                        isError = uiState.isConflict,
                        onClick = onStartDateClick
                    )
                    DateRow(
                        label = stringResource(Res.string.label_ends),
                        date = endDate,
                        isError = uiState.isConflict,
                        onClick = onEndDateClick
                    )

                    if (uiState.isConflict) {
                        Text(
                            text = uiState.conflictingSeasonName
                                ?.let { stringResource(Res.string.msg_create_season_conflict, it) }
                                ?: stringResource(Res.string.msg_create_season_conflict_unknown),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionLabel(stringResource(Res.string.label_name_section))
                        Text(
                            text = stringResource(Res.string.label_optional),
                            style = MaterialTheme.typography.bodySmall,
                            color = DeepCarbon.copy(alpha = 0.5f)
                        )
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = onNameChange,
                        label = { Text(stringResource(Res.string.label_season_name_field)) },
                        placeholder = {
                            Text(
                                stringResource(
                                    Res.string.label_season_name_placeholder,
                                    inferredName
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = FormRoundedShape,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = submitKeyboardActions { if (!uiState.isSubmitting) onCreateClick() },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = DeepCarbon,
                            unfocusedTextColor = DeepCarbon,
                            focusedBorderColor = PitchGreen,
                            unfocusedBorderColor = CarbonGray.copy(alpha = 0.3f),
                            focusedLabelColor = PitchGreen,
                            unfocusedLabelColor = CarbonGray
                        )
                    )

                    Text(
                        text = stringResource(Res.string.msg_season_name_helper, inferredName),
                        style = MaterialTheme.typography.bodySmall,
                        color = DeepCarbon.copy(alpha = 0.6f)
                    )

                    if (uiState.error != null) {
                        Text(
                            text = uiState.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    PrimaryLoadingButton(
                        text = Res.string.btn_create_season,
                        isLoading = uiState.isSubmitting,
                        onClick = onCreateClick,
                        submittingText = Res.string.btn_creating_season
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    if (showStartPicker) {
        // Can't pick a start date after the current end date -- see DatePickerSheet's minDate/maxDate.
        DatePickerSheet(
            initialDate = startDate,
            title = Res.string.label_select_start_date,
            maxDate = endDate,
            onConfirm = onStartDateConfirmed,
            onDismiss = onDismissStartPicker
        )
    }
    if (showEndPicker) {
        // Can't pick an end date before the current start date.
        DatePickerSheet(
            initialDate = endDate,
            title = Res.string.label_select_end_date,
            minDate = startDate,
            onConfirm = onEndDateConfirmed,
            onDismiss = onDismissEndPicker
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = DeepCarbon.copy(alpha = 0.5f)
    )
}

@Composable
private fun SeasonTypeRow(
    title: String,
    rangeLabel: String,
    valuePill: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = FormRoundedShape,
        color = if (selected) PitchGreen.copy(alpha = 0.08f) else Color.White,
        border = BorderStroke(1.dp, if (selected) PitchGreen else CarbonGray.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = PitchGreen,
                    unselectedColor = CarbonGray.copy(alpha = 0.4f)
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = DeepCarbon
                )
                Text(
                    text = rangeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = DeepCarbon.copy(alpha = 0.6f)
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (selected) PitchGreen.copy(alpha = 0.15f) else Color.Transparent
            ) {
                Text(
                    text = valuePill,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (selected) PitchGreen else DeepCarbon.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun DateRow(label: String, date: LocalDate, isError: Boolean, onClick: () -> Unit) {
    val contentColor = if (isError) MaterialTheme.colorScheme.error else DeepCarbon
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(
            1.dp,
            if (isError) MaterialTheme.colorScheme.error else CarbonGray.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = DeepCarbon.copy(alpha = 0.5f)
                )
                Text(
                    text = date.format(DateDisplayFormat),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = contentColor
                )
            }
            Text(text = "›", color = DeepCarbon.copy(alpha = 0.4f), fontWeight = FontWeight.Bold)
        }
    }
}

@Preview
@Composable
private fun CreateSeasonContentPreviewLight() {
    val today = remember { LocalDate(2026, 9, 4) }
    val (start, end) = remember { SeasonDateDefaults.datesFor(SeasonType.EUROPEAN_YEAR, today) }
    SquadifyTheme(darkTheme = false) {
        CreateSeasonContent(
            uiState = CreateSeasonUiState.Idle(),
            clubName = "CD Ribamar",
            today = today,
            selectedType = SeasonType.EUROPEAN_YEAR,
            startDate = start,
            endDate = end,
            name = "",
            showStartPicker = false,
            showEndPicker = false,
            onNavigateBack = {},
            onTypeSelected = {},
            onNameChange = {},
            onStartDateClick = {},
            onEndDateClick = {},
            onStartDateConfirmed = {},
            onEndDateConfirmed = {},
            onDismissStartPicker = {},
            onDismissEndPicker = {},
            onCreateClick = {}
        )
    }
}

// Screens in this app always render a light content area regardless of system theme (only the header
// bars are dark) -- this preview exists to prove that stays true even when SquadifyTheme
// resolves its dark color scheme, per AI_GUIDELINES.md "Compose Previews & Orientation Support".
@Preview
@Composable
private fun CreateSeasonContentPreviewDarkSystemTheme() {
    val today = remember { LocalDate(2026, 9, 4) }
    val (start, end) = remember { SeasonDateDefaults.datesFor(SeasonType.CALENDAR_YEAR, today) }
    SquadifyTheme(darkTheme = true) {
        CreateSeasonContent(
            uiState = CreateSeasonUiState.Idle(isConflict = true, conflictingSeasonName = "2026"),
            clubName = "CD Ribamar",
            today = today,
            selectedType = SeasonType.CALENDAR_YEAR,
            startDate = start,
            endDate = end,
            name = "",
            showStartPicker = false,
            showEndPicker = false,
            onNavigateBack = {},
            onTypeSelected = {},
            onNameChange = {},
            onStartDateClick = {},
            onEndDateClick = {},
            onStartDateConfirmed = {},
            onEndDateConfirmed = {},
            onDismissStartPicker = {},
            onDismissEndPicker = {},
            onCreateClick = {}
        )
    }
}

@Preview(widthDp = 780, heightDp = 360)
@Composable
private fun CreateSeasonContentPreviewLandscape() {
    val today = remember { LocalDate(2026, 9, 4) }
    val (start, end) = remember { SeasonDateDefaults.datesFor(SeasonType.EUROPEAN_YEAR, today) }
    SquadifyTheme(darkTheme = false) {
        CreateSeasonContent(
            uiState = CreateSeasonUiState.Idle(),
            clubName = "CD Ribamar",
            today = today,
            selectedType = SeasonType.EUROPEAN_YEAR,
            startDate = start,
            endDate = end,
            name = "",
            showStartPicker = false,
            showEndPicker = false,
            onNavigateBack = {},
            onTypeSelected = {},
            onNameChange = {},
            onStartDateClick = {},
            onEndDateClick = {},
            onStartDateConfirmed = {},
            onEndDateConfirmed = {},
            onDismissStartPicker = {},
            onDismissEndPicker = {},
            onCreateClick = {}
        )
    }
}
