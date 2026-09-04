package com.ricardofrango.footballmanager.presentation.ui.createseason

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ricardofrango.footballmanager.domain.SeasonDateDefaults
import com.ricardofrango.footballmanager.model.SeasonType
import com.ricardofrango.footballmanager.presentation.ui.theme.CarbonGray
import com.ricardofrango.footballmanager.presentation.ui.theme.DeepCarbon
import com.ricardofrango.footballmanager.presentation.ui.theme.PitchGreen
import footballmanager.app.shared.generated.resources.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock
import kotlin.time.Instant

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
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSeasonScreen(
    clubId: String,
    clubName: String,
    onNavigateBack: () -> Unit,
    onSeasonCreated: () -> Unit,
    viewModel: CreateSeasonViewModel = koinViewModel()
) {
    val uiState by viewModel.state.collectAsState()
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

    val inferredName = SeasonDateDefaults.inferredName(selectedType, startDate, endDate)
    val europeanDefaults = remember(today) { SeasonDateDefaults.datesFor(SeasonType.EUROPEAN_YEAR, today) }
    val calendarDefaults = remember(today) { SeasonDateDefaults.datesFor(SeasonType.CALENDAR_YEAR, today) }
    val europeanPill = SeasonDateDefaults.inferredName(SeasonType.EUROPEAN_YEAR, europeanDefaults.first, europeanDefaults.second)
    val calendarPill = SeasonDateDefaults.inferredName(SeasonType.CALENDAR_YEAR, calendarDefaults.first, calendarDefaults.second)

    fun applyType(type: SeasonType) {
        selectedTypeName = type.name
        val (start, end) = SeasonDateDefaults.datesFor(type, today)
        startDateIso = start.toString()
        endDateIso = end.toString()
        viewModel.clearSubmitError()
    }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(Res.string.title_create_season),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = clubName,
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.7f))
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_back),
                            contentDescription = stringResource(Res.string.label_back),
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CarbonGray,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is CreateSeasonUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
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
                        onClick = { applyType(SeasonType.EUROPEAN_YEAR) }
                    )
                    SeasonTypeRow(
                        title = stringResource(Res.string.label_season_type_calendar),
                        rangeLabel = stringResource(Res.string.label_season_type_calendar_range),
                        valuePill = calendarPill,
                        selected = selectedType == SeasonType.CALENDAR_YEAR,
                        onClick = { applyType(SeasonType.CALENDAR_YEAR) }
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
                        isError = state.isConflict,
                        onClick = { showStartPicker = true }
                    )
                    DateRow(
                        label = stringResource(Res.string.label_ends),
                        date = endDate,
                        isError = state.isConflict,
                        onClick = { showEndPicker = true }
                    )

                    if (state.isConflict) {
                        Text(
                            text = state.conflictingSeasonName
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
                        onValueChange = {
                            name = it
                            viewModel.clearSubmitError()
                        },
                        label = { Text(stringResource(Res.string.label_season_name_field)) },
                        placeholder = {
                            Text(stringResource(Res.string.label_season_name_placeholder, inferredName))
                        },
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

                    Text(
                        text = stringResource(Res.string.msg_season_name_helper, inferredName),
                        style = MaterialTheme.typography.bodySmall,
                        color = DeepCarbon.copy(alpha = 0.6f)
                    )

                    if (state.error != null) {
                        Text(
                            text = state.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            viewModel.onCreateClicked(
                                clubId = clubId,
                                type = selectedType,
                                startDate = startDate,
                                endDate = endDate,
                                name = name
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(58.dp),
                        enabled = !state.isSubmitting,
                        shape = FormRoundedShape,
                        colors = ButtonDefaults.buttonColors(containerColor = PitchGreen, contentColor = Color.White)
                    ) {
                        if (state.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 3.dp
                            )
                        } else {
                            Text(
                                text = stringResource(Res.string.btn_create_season).uppercase(),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    if (showStartPicker) {
        SeasonDatePickerSheet(
            initialDate = startDate,
            onConfirm = {
                startDateIso = it.toString()
                showStartPicker = false
                viewModel.clearSubmitError()
            },
            onDismiss = { showStartPicker = false }
        )
    }
    if (showEndPicker) {
        SeasonDatePickerSheet(
            initialDate = endDate,
            onConfirm = {
                endDateIso = it.toString()
                showEndPicker = false
                viewModel.clearSubmitError()
            },
            onDismiss = { showEndPicker = false }
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
        border = BorderStroke(1.dp, if (isError) MaterialTheme.colorScheme.error else CarbonGray.copy(alpha = 0.15f))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeasonDatePickerSheet(
    initialDate: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White
    ) {
        DatePicker(state = datePickerState, showModeToggle = false)
        Button(
            onClick = {
                val millis = datePickerState.selectedDateMillis
                if (millis != null) {
                    onConfirm(Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date)
                } else {
                    onDismiss()
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PitchGreen, contentColor = Color.White)
        ) {
            Text(stringResource(Res.string.btn_confirm))
        }
    }
}
