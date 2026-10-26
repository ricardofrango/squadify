package com.ricardofrango.squadify.presentation.ui.training

import com.ricardofrango.squadify.presentation.ui.theme.ScreenBackground
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.Country
import com.ricardofrango.squadify.model.Holiday
import com.ricardofrango.squadify.model.ReplacedSession
import com.ricardofrango.squadify.model.TrainingSessionPreviewItem
import com.ricardofrango.squadify.model.TrainingSessions
import com.ricardofrango.squadify.model.TrainingSessionsPreview
import com.ricardofrango.squadify.presentation.ui.components.SquadifyTopBar
import com.ricardofrango.squadify.presentation.ui.components.FormErrorLine
import com.ricardofrango.squadify.presentation.ui.components.FormTextField
import com.ricardofrango.squadify.presentation.ui.components.PrimaryLoadingButton
import com.ricardofrango.squadify.presentation.ui.components.StatsCard
import com.ricardofrango.squadify.presentation.ui.components.StatsHeading
import com.ricardofrango.squadify.presentation.ui.components.StatsSecondaryText
import com.ricardofrango.squadify.presentation.ui.components.TimePickerSheet
import com.ricardofrango.squadify.presentation.ui.theme.AlertRed
import com.ricardofrango.squadify.presentation.ui.theme.AmberText
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.ScreenPadding
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyClubTheme
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import com.ricardofrango.squadify.presentation.ui.theme.screenContentPadding
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.btn_add_another_day
import squadify.app.shared.generated.resources.btn_back_to_preview
import squadify.app.shared.generated.resources.btn_create_n_sessions
import squadify.app.shared.generated.resources.btn_creating_n_sessions
import squadify.app.shared.generated.resources.btn_edit
import squadify.app.shared.generated.resources.btn_preview_n_sessions
import squadify.app.shared.generated.resources.cd_remove
import squadify.app.shared.generated.resources.country_portugal
import squadify.app.shared.generated.resources.ic_close
import squadify.app.shared.generated.resources.label_also_holidays
import squadify.app.shared.generated.resources.label_end_date_required
import squadify.app.shared.generated.resources.label_end_required
import squadify.app.shared.generated.resources.label_location_field
import squadify.app.shared.generated.resources.label_location_section
import squadify.app.shared.generated.resources.label_n_sessions
import squadify.app.shared.generated.resources.label_period
import squadify.app.shared.generated.resources.label_pick_time
import squadify.app.shared.generated.resources.label_section_count
import squadify.app.shared.generated.resources.label_slot_n
import squadify.app.shared.generated.resources.label_start_date_required
import squadify.app.shared.generated.resources.label_start_required
import squadify.app.shared.generated.resources.label_training_days
import squadify.app.shared.generated.resources.msg_end_before_start
import squadify.app.shared.generated.resources.msg_holiday_skipped
import squadify.app.shared.generated.resources.msg_holidays_helper
import squadify.app.shared.generated.resources.msg_location_stadium_helper
import squadify.app.shared.generated.resources.msg_preview_summary
import squadify.app.shared.generated.resources.msg_replace_content
import squadify.app.shared.generated.resources.msg_replace_loses_both
import squadify.app.shared.generated.resources.msg_replace_loses_exercises
import squadify.app.shared.generated.resources.msg_replace_loses_ratings
import squadify.app.shared.generated.resources.msg_replaces_session
import squadify.app.shared.generated.resources.title_create_sessions
import squadify.app.shared.generated.resources.title_preview
import squadify.app.shared.generated.resources.title_replace_content

/**
 * Create sessions (design 39c/d), two steps:
 * - Form: weekly slots (weekdays + start + end), the period, the location (the club's stadium by default)
 *   and "Also on public holidays". The button says how many sessions it'll make.
 * - Preview: every date by month, skipped holidays named, sessions it replaces flagged (in red when their
 *   exercises or ratings will be deleted). "Edit" goes back with everything kept; "Create" asks first if
 *   anything with content would be replaced.
 * [onCreated] gets how many were created.
 */
@Composable
fun CreateSessionsScreen(
    ageGroupId: String,
    onNavigateBack: () -> Unit,
    onCreated: (count: Int) -> Unit,
    viewModel: CreateSessionsViewModel = koinViewModel()
) {
    SquadifyClubTheme {
        val state by viewModel.state.collectAsStateWithLifecycle()
        LaunchedEffect(ageGroupId) { viewModel.start(ageGroupId) }
        LaunchedEffect(viewModel) { viewModel.created.collect { onCreated(it) } }
        CreateSessionsContent(
            state = state,
            onNavigateBack = { if (state.preview != null) viewModel.backToForm() else onNavigateBack() },
            onRetry = { viewModel.start(ageGroupId, force = true) },
            onFormChange = viewModel::onFormChange,
            onPreview = viewModel::preview,
            onEdit = viewModel::backToForm,
            onCreate = viewModel::create,
            onConfirm = viewModel::confirmCreate,
            onDismissConfirm = viewModel::dismissConfirm
        )
    }
}

/** "Sub-15 · CD Ribamar". */
internal fun TrainingSessions.subtitle(): String = "${ageGroup.name} · $clubName"

@Composable
private fun CreateSessionsContent(
    state: CreateSessionsUiState,
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit,
    onFormChange: (CreateSessionsForm) -> Unit,
    onPreview: () -> Unit,
    onEdit: () -> Unit,
    onCreate: () -> Unit,
    onConfirm: () -> Unit,
    onDismissConfirm: () -> Unit
) {
    val preview = state.preview
    Scaffold(
        containerColor = ScreenBackground,
        topBar = {
            SquadifyTopBar(
                title = stringResource(if (preview != null) Res.string.title_preview else Res.string.title_create_sessions),
                subtitle = state.sessions?.subtitle(),
                onNavigateBack = onNavigateBack
            )
        }
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().background(ScreenBackground)
        val sessions = state.sessions
        if (sessions == null) {
            PlanLoadingOrError(state.loading, modifier, onRetry)
            return@Scaffold
        }
        Column(modifier) {
            if (preview == null) {
                BoxWithConstraints(Modifier.weight(1f)) {
                    val wide = maxWidth >= 600.dp
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).screenContentPadding()) {
                        EditorColumns(
                            wide = wide,
                            left = { SlotsSection(state.form, !state.previewing, onFormChange) },
                            right = { PeriodAndPlace(state.form, sessions, !state.previewing, onFormChange) }
                        )
                    }
                }
                FormErrorLine(state.error)
                BottomBar {
                    PrimaryLoadingButton(
                        text = stringResource(Res.string.btn_preview_n_sessions, state.count),
                        submittingText = stringResource(Res.string.btn_preview_n_sessions, state.count),
                        isLoading = state.previewing,
                        enabled = state.canPreview,
                        onClick = onPreview
                    )
                }
            } else {
                PreviewList(preview, state.form, Modifier.weight(1f))
                FormErrorLine(state.error)
                BottomBar {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = onEdit, enabled = !state.creating, shape = RoundedCornerShape(14.dp)) {
                            Text(stringResource(Res.string.btn_edit), color = DeepCarbon)
                        }
                        val n = preview.toCreate.size
                        PrimaryLoadingButton(
                            text = stringResource(Res.string.btn_create_n_sessions, n),
                            submittingText = stringResource(Res.string.btn_creating_n_sessions, n),
                            isLoading = state.creating,
                            enabled = n > 0,
                            onClick = onCreate,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
    if (state.askConfirm && preview != null) {
        AlertDialog(
            onDismissRequest = onDismissConfirm,
            title = { Text(stringResource(Res.string.title_replace_content, preview.replacingWithContent)) },
            text = { Text(stringResource(Res.string.msg_replace_content, preview.replacingWithContent)) },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(stringResource(Res.string.btn_create_n_sessions, preview.toCreate.size), color = AlertRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = onDismissConfirm) { Text(stringResource(Res.string.btn_back_to_preview), color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        )
    }
}

@Composable
private fun BottomBar(content: @Composable () -> Unit) {
    Surface(color = Color.White, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = ScreenPadding.Horizontal, vertical = 14.dp)) { content() }
    }
}

@Composable
private fun SlotsSection(form: CreateSessionsForm, enabled: Boolean, onFormChange: (CreateSessionsForm) -> Unit) {
    StatsHeading(stringResource(Res.string.label_training_days))
    form.slots.forEachIndexed { i, slot ->
        SlotEditor(
            index = i, slot = slot, enabled = enabled, removable = form.slots.size > 1,
            onChange = { changed -> onFormChange(form.copy(slots = form.slots.toMutableList().also { it[i] = changed })) },
            onRemove = { onFormChange(form.copy(slots = form.slots.filterIndexed { j, _ -> j != i })) }
        )
    }
    AddRowButton(stringResource(Res.string.btn_add_another_day), enabled) {
        onFormChange(form.copy(slots = form.slots + SlotForm(start = form.slots.lastOrNull()?.start ?: LocalTime(18, 0), end = form.slots.lastOrNull()?.end ?: LocalTime(19, 0))))
    }
}

@Composable
private fun SlotEditor(index: Int, slot: SlotForm, enabled: Boolean, removable: Boolean, onChange: (SlotForm) -> Unit, onRemove: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.15f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.label_slot_n, index + 1), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = DeepCarbon, modifier = Modifier.weight(1f))
                if (removable) {
                    IconButton(onClick = onRemove, enabled = enabled) {
                        Icon(painterResource(Res.drawable.ic_close), stringResource(Res.string.cd_remove), tint = DeepCarbon.copy(alpha = 0.5f))
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..7).forEach { day ->
                    val selected = day in slot.weekdays
                    FilterChip(
                        selected = selected,
                        onClick = { onChange(slot.copy(weekdays = if (selected) slot.weekdays - day else slot.weekdays + day)) },
                        enabled = enabled,
                        label = { Text(weekdayShort(day)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PitchGreen, selectedLabelColor = Color.White)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TimeField(stringResource(Res.string.label_start_required), slot.start, enabled, Modifier.weight(1f)) { onChange(slot.copy(start = it)) }
                TimeField(stringResource(Res.string.label_end_required), slot.end, enabled, Modifier.weight(1f)) { onChange(slot.copy(end = it)) }
            }
            if (slot.end <= slot.start) {
                Text(stringResource(Res.string.msg_end_before_start), style = MaterialTheme.typography.bodySmall, color = AlertRed)
            }
        }
    }
}

/** A time that opens the picker. */
@Composable
fun TimeField(label: String, time: LocalTime, enabled: Boolean, modifier: Modifier = Modifier, onPick: (LocalTime) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Surface(
        onClick = { picking = true },
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = DeepCarbon.copy(alpha = 0.5f))
            Text(time.hhmm(), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = DeepCarbon)
        }
    }
    if (picking) {
        TimePickerSheet(title = Res.string.label_pick_time, initialTime = time, onConfirm = { picking = false; onPick(it) }, onDismiss = { picking = false })
    }
}

@Composable
private fun PeriodAndPlace(form: CreateSessionsForm, sessions: TrainingSessions, enabled: Boolean, onFormChange: (CreateSessionsForm) -> Unit) {
    // Not bound by the season's dates: those are the association's calendar, and clubs often train past them.
    val season = sessions.season
    StatsHeading(stringResource(Res.string.label_period))
    val start = form.startDate
    val end = form.endDate
    val error = PlanDateError.END_BEFORE_START.takeIf { start != null && end != null && end < start }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PlanDateField(stringResource(Res.string.label_start_date_required), start, season, enabled,
            error = null, modifier = Modifier.weight(1f)) { onFormChange(form.copy(startDate = it)) }
        PlanDateField(stringResource(Res.string.label_end_date_required), end, season, enabled,
            error = error, modifier = Modifier.weight(1f)) { onFormChange(form.copy(endDate = it)) }
    }
    StatsHeading(stringResource(Res.string.label_location_section))
    FormTextField(
        value = form.location,
        onValueChange = { onFormChange(form.copy(location = it)) },
        label = stringResource(Res.string.label_location_field),
        supporting = stringResource(Res.string.msg_location_stadium_helper),
        enabled = enabled,
        keyboardType = KeyboardType.Text,
        imeAction = ImeAction.Done
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(Res.string.label_also_holidays), style = MaterialTheme.typography.bodyLarge, color = DeepCarbon)
            Text(stringResource(Res.string.msg_holidays_helper, countryName(sessions.country)), style = MaterialTheme.typography.bodySmall, color = StatsSecondaryText)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = form.includeHolidays,
            onCheckedChange = { onFormChange(form.copy(includeHolidays = it)) },
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = PitchGreen,
                checkedBorderColor = PitchGreen,
                uncheckedThumbColor = CarbonGray.copy(alpha = 0.45f),
                uncheckedTrackColor = Color.White,
                uncheckedBorderColor = CarbonGray.copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
internal fun countryName(country: Country): String = when (country) {
    Country.PORTUGAL -> stringResource(Res.string.country_portugal)
}

@Composable
private fun PreviewList(preview: TrainingSessionsPreview, form: CreateSessionsForm, modifier: Modifier) {
    val byMonth = preview.items.groupBy { LocalDate(it.date.year, it.date.month, 1) }
    LazyColumn(modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            StatsCard {
                Text(stringResource(Res.string.label_n_sessions, preview.toCreate.size), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
                val dayNames = (1..7).associateWith { weekdayShort(it) }
                val slots = form.slots.joinToString(" · ") { s -> "${s.weekdays.sorted().joinToString("/") { dayNames.getValue(it) }} ${timeRange(s.start, s.end)}" }
                Text(listOfNotNull(slots, form.location.trim().ifEmpty { null }).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = StatsSecondaryText)
                Text(stringResource(Res.string.msg_preview_summary, preview.skippedHolidays, preview.replacing), style = MaterialTheme.typography.bodySmall, color = StatsSecondaryText)
            }
        }
        byMonth.forEach { (month, items) ->
            item(key = "m-$month") {
                StatsHeading(
                    stringResource(Res.string.label_section_count, "${monthName(month)} ${month.year}", items.count { it.skippedHoliday == null }),
                    Modifier.padding(top = 10.dp, bottom = 2.dp)
                )
            }
            items(items, key = { "${it.date}-${it.startTime}" }) { PreviewRow(it) }
        }
    }
}

@Composable
private fun PreviewRow(item: TrainingSessionPreviewItem) {
    val skipped = item.skippedHoliday
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${weekdayShort(item.date.dayOfWeek.isoDayNumber)} ${item.date.day}",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (skipped != null) DeepCarbon.copy(alpha = 0.35f) else DeepCarbon,
                modifier = Modifier.width(72.dp)
            )
            Text(
                if (skipped != null) stringResource(Res.string.msg_holiday_skipped, holidayName(skipped)) else timeRange(item.startTime, item.endTime),
                style = MaterialTheme.typography.bodyMedium,
                color = if (skipped != null) DeepCarbon.copy(alpha = 0.35f) else DeepCarbon
            )
        }
        item.replaces?.let { replaced ->
            Column(Modifier.padding(start = 72.dp)) {
                Text(stringResource(Res.string.msg_replaces_session), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = AmberText)
                replacedContent(replaced)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = AlertRed) }
            }
        }
    }
}

/** "Has 4 exercises and 18 ratings, which will be deleted", or null when the replaced session is empty. */
@Composable
internal fun replacedContent(replaced: ReplacedSession): String? = when {
    replaced.exerciseCount > 0 && replaced.ratingCount > 0 -> stringResource(Res.string.msg_replace_loses_both, replaced.exerciseCount, replaced.ratingCount)
    replaced.exerciseCount > 0 -> stringResource(Res.string.msg_replace_loses_exercises, replaced.exerciseCount)
    replaced.ratingCount > 0 -> stringResource(Res.string.msg_replace_loses_ratings, replaced.ratingCount)
    else -> null
}

// ---- Previews ----

private val previewSessions = TrainingSessions(
    ageGroup = AgeGroup("ag1", "c1", "Sub-15", "s1", 2012, 2012),
    clubName = "CD Ribamar",
    season = previewSeason,
    stadiumLocation = "Campo do Ribamar",
    sessions = emptyList(),
    canEdit = true,
    canRate = true
)

private val previewForm = CreateSessionsForm(
    slots = listOf(SlotForm(setOf(2), LocalTime(18, 0), LocalTime(19, 0)), SlotForm(setOf(5), LocalTime(18, 30), LocalTime(20, 0))),
    startDate = LocalDate(2026, 10, 6), endDate = LocalDate(2027, 1, 8), location = "Campo do Ribamar"
)

@Preview
@Composable
private fun CreateSessionsFormPreview() {
    SquadifyTheme { CreateSessionsContent(CreateSessionsUiState(loading = false, sessions = previewSessions, form = previewForm), {}, {}, {}, {}, {}, {}, {}, {}) }
}

@Preview
@Composable
private fun CreateSessionsPreviewPreview() {
    val preview = TrainingSessionsPreview(
        listOf(
            TrainingSessionPreviewItem(LocalDate(2026, 10, 6), LocalTime(18, 0), LocalTime(19, 0)),
            TrainingSessionPreviewItem(LocalDate(2026, 10, 9), LocalTime(18, 30), LocalTime(20, 0), replaces = ReplacedSession("x", 4, 18)),
            TrainingSessionPreviewItem(LocalDate(2026, 12, 1), LocalTime(18, 0), LocalTime(19, 0), skippedHoliday = Holiday.RESTORATION_OF_INDEPENDENCE)
        )
    )
    SquadifyTheme { CreateSessionsContent(CreateSessionsUiState(loading = false, sessions = previewSessions, form = previewForm, preview = preview), {}, {}, {}, {}, {}, {}, {}, {}) }
}

@Preview(widthDp = 900, heightDp = 480)
@Composable
private fun CreateSessionsLandscapePreview() {
    SquadifyTheme { CreateSessionsContent(CreateSessionsUiState(loading = false, sessions = previewSessions, form = previewForm), {}, {}, {}, {}, {}, {}, {}, {}) }
}
