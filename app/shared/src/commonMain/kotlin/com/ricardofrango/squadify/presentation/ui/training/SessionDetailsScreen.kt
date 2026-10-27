package com.ricardofrango.squadify.presentation.ui.training

import com.ricardofrango.squadify.presentation.ui.components.isWide
import com.ricardofrango.squadify.presentation.ui.theme.ScreenBackground
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.TrainingIntensity
import com.ricardofrango.squadify.model.TrainingPlan
import com.ricardofrango.squadify.model.TrainingPlanLevel
import com.ricardofrango.squadify.model.TrainingSession
import com.ricardofrango.squadify.model.TrainingSessionDetails
import com.ricardofrango.squadify.model.mondayOf
import com.ricardofrango.squadify.model.periodEnd
import com.ricardofrango.squadify.presentation.ui.components.SquadifyTopBar
import com.ricardofrango.squadify.presentation.ui.components.FormErrorLine
import com.ricardofrango.squadify.presentation.ui.components.StatsCard
import com.ricardofrango.squadify.presentation.ui.components.StatsHeading
import com.ricardofrango.squadify.presentation.ui.components.StatsSecondaryText
import com.ricardofrango.squadify.presentation.ui.components.shortDateLabel
import com.ricardofrango.squadify.presentation.ui.opponents.mapsUrl
import com.ricardofrango.squadify.presentation.ui.theme.AlertRed
import com.ricardofrango.squadify.presentation.ui.theme.Amber
import com.ricardofrango.squadify.presentation.ui.theme.AmberText
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyClubTheme
import com.ricardofrango.squadify.presentation.ui.theme.SuccessGreen
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import com.ricardofrango.squadify.presentation.ui.theme.screenContentPadding
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.btn_cancel_session
import squadify.app.shared.generated.resources.btn_delete
import squadify.app.shared.generated.resources.btn_keep_it
import squadify.app.shared.generated.resources.btn_open_in_maps
import squadify.app.shared.generated.resources.btn_plan_the_week
import squadify.app.shared.generated.resources.btn_restore
import squadify.app.shared.generated.resources.btn_week_plan
import squadify.app.shared.generated.resources.cd_edit_plan
import squadify.app.shared.generated.resources.cd_more_options
import squadify.app.shared.generated.resources.ic_edit
import squadify.app.shared.generated.resources.ic_more
import squadify.app.shared.generated.resources.label_notes_field
import squadify.app.shared.generated.resources.label_this_week
import squadify.app.shared.generated.resources.label_training_subtitle
import squadify.app.shared.generated.resources.msg_cancel_session
import squadify.app.shared.generated.resources.msg_delete_session
import squadify.app.shared.generated.resources.msg_session_cancelled
import squadify.app.shared.generated.resources.msg_session_cancelled_by
import squadify.app.shared.generated.resources.msg_week_no_plan
import squadify.app.shared.generated.resources.status_cancelled
import squadify.app.shared.generated.resources.status_ended
import squadify.app.shared.generated.resources.status_in_days
import squadify.app.shared.generated.resources.status_today
import squadify.app.shared.generated.resources.status_upcoming
import squadify.app.shared.generated.resources.title_cancel_session
import squadify.app.shared.generated.resources.title_delete_session
import kotlin.time.Clock
import squadify.app.shared.generated.resources.cd_remove
import squadify.app.shared.generated.resources.ic_close
import squadify.app.shared.generated.resources.msg_exercises_total
import squadify.app.shared.generated.resources.msg_session_no_exercises
import squadify.app.shared.generated.resources.btn_add_exercise
import squadify.app.shared.generated.resources.label_exercises
import squadify.app.shared.generated.resources.label_section_count
import squadify.app.shared.generated.resources.btn_rate_players
import squadify.app.shared.generated.resources.label_rated_count
import squadify.app.shared.generated.resources.msg_ratings_open_at
import squadify.app.shared.generated.resources.msg_ratings_cancelled
import squadify.app.shared.generated.resources.label_player_ratings_staff

/**
 * One training session (design 39f / 40f): when and where (Open in Maps) and its status; for staff, the
 * week's focus and intensity with the month's focus (or "This week has no plan"); then its notes.
 * With `MANAGE_TRAINING`: the pencil, and ⋮ with Cancel session and Delete. A cancelled session shows
 * who cancelled it and when, with Restore, and loses the pencil.
 */
@Composable
fun SessionDetailsScreen(
    sessionId: String,
    onNavigateBack: () -> Unit,
    onEdit: (ageGroupId: String) -> Unit,
    onOpenWeekPlan: (ageGroupId: String, monday: LocalDate, edit: Boolean) -> Unit,
    onAddExercises: () -> Unit,
    onRatePlayers: () -> Unit,
    // A cancel, restore or delete happened: the screens underneath should reload.
    onChanged: (deleted: Boolean) -> Unit,
    refresh: Boolean = false,
    viewModel: SessionDetailsViewModel = koinViewModel()
) {
    SquadifyClubTheme {
        val state by viewModel.state.collectAsStateWithLifecycle()
        LaunchedEffect(sessionId) { viewModel.load(sessionId) }
        LaunchedEffect(refresh) { if (refresh) viewModel.load(sessionId, force = true) }
        LaunchedEffect(viewModel) { viewModel.changed.collect { onChanged(it) } }
        SessionDetailsContent(
            state = state,
            onNavigateBack = onNavigateBack,
            onRetry = { viewModel.load(sessionId, force = true) },
            onEdit = { state.details?.let { onEdit(it.ageGroup.id) } },
            onOpenWeekPlan = { edit -> state.details?.let { onOpenWeekPlan(it.ageGroup.id, mondayOf(it.session.date), edit) } },
            onCancel = viewModel::cancel,
            onRestore = viewModel::restore,
            onDelete = viewModel::delete,
            onAddExercises = onAddExercises,
            onSetExercises = viewModel::setExercises,
            onRatePlayers = onRatePlayers
        )
    }
}

/** "Thu 15 Oct". */
@Composable
internal fun sessionTitle(date: LocalDate): String = "${weekdayShort(date.dayOfWeek.isoDayNumber)} ${shortDateLabel(date)}"

/** Upcoming · in 3 days / Today / Ended / Cancelled. */
@Composable
internal fun sessionStatus(session: TrainingSession, now: LocalDateTime): String = when {
    session.cancelled -> stringResource(Res.string.status_cancelled)
    session.ended(now) -> stringResource(Res.string.status_ended)
    session.date == now.date -> stringResource(Res.string.status_today)
    else -> {
        val days = now.date.daysUntil(session.date)
        "${stringResource(Res.string.status_upcoming)} · ${pluralStringResource(Res.plurals.status_in_days, days, days)}"
    }
}

@Composable
private fun SessionDetailsContent(
    state: SessionDetailsUiState,
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onOpenWeekPlan: (edit: Boolean) -> Unit,
    onCancel: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onAddExercises: () -> Unit = {},
    onSetExercises: (List<String>) -> Unit = {},
    onRatePlayers: () -> Unit = {}
) {
    val details = state.details
    var askCancel by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = ScreenBackground,
        topBar = {
            SquadifyTopBar(
                title = details?.let { sessionTitle(it.session.date) } ?: "",
                subtitle = details?.let { stringResource(Res.string.label_training_subtitle, it.ageGroup.name, it.clubName) },
                onNavigateBack = onNavigateBack,
                actions = {
                    if (details?.canEdit == true) {
                        if (!details.session.cancelled) {
                            IconButton(onClick = onEdit, enabled = !state.busy) { Icon(painterResource(Res.drawable.ic_edit), stringResource(Res.string.cd_edit_plan), tint = Color.White) }
                        }
                        var open by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { open = true }, enabled = !state.busy) { Icon(painterResource(Res.drawable.ic_more), stringResource(Res.string.cd_more_options), tint = Color.White) }
                            DropdownMenu(open, onDismissRequest = { open = false }, modifier = Modifier.background(Color.White)) {
                                if (!details.session.cancelled) {
                                    DropdownMenuItem(text = { Text(stringResource(Res.string.btn_cancel_session), color = DeepCarbon) }, onClick = { open = false; askCancel = true })
                                }
                                DropdownMenuItem(text = { Text(stringResource(Res.string.btn_delete), color = AlertRed) }, onClick = { open = false; askDelete = true })
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding).background(ScreenBackground)
        if (details == null) {
            PlanLoadingOrError(state.loading, modifier, onRetry)
            return@Scaffold
        }
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        BoxWithConstraints(modifier) {
            val wide = isWide
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).screenContentPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (details.session.cancelled) CancelledBanner(details, details.canEdit, onRestore)
                // Once the session has ended, its ratings come first (design 39f).
                val ended = details.session.ended(now)
                if (ended) RatingsCard(details, now, onRatePlayers)
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) { HeaderCard(details, now); WeekStrip(details, onOpenWeekPlan) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ExercisesCard(details, !state.busy, onAddExercises, onSetExercises)
                            if (!ended) RatingsCard(details, now, onRatePlayers)
                            NotesCard(details.session)
                        }
                    }
                } else {
                    HeaderCard(details, now)
                    WeekStrip(details, onOpenWeekPlan)
                    ExercisesCard(details, !state.busy, onAddExercises, onSetExercises)
                    if (!ended) RatingsCard(details, now, onRatePlayers)
                    NotesCard(details.session)
                }
                FormErrorLine(state.error)
            }
        }
    }
    if (askCancel && details != null) {
        AlertDialog(
            onDismissRequest = { askCancel = false },
            title = { Text(stringResource(Res.string.title_cancel_session)) },
            text = { Text(stringResource(Res.string.msg_cancel_session, sessionTitle(details.session.date))) },
            confirmButton = { TextButton(onClick = { askCancel = false; onCancel() }) { Text(stringResource(Res.string.btn_cancel_session), color = AlertRed, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { askCancel = false }) { Text(stringResource(Res.string.btn_keep_it), color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        )
    }
    if (askDelete) {
        AlertDialog(
            onDismissRequest = { askDelete = false },
            title = { Text(stringResource(Res.string.title_delete_session)) },
            text = { Text(stringResource(Res.string.msg_delete_session)) },
            confirmButton = { TextButton(onClick = { askDelete = false; onDelete() }) { Text(stringResource(Res.string.btn_delete), color = AlertRed, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { askDelete = false }) { Text(stringResource(Res.string.btn_keep_it), color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        )
    }
}

@Composable
private fun HeaderCard(details: TrainingSessionDetails, now: LocalDateTime) {
    val session = details.session
    val uriHandler = LocalUriHandler.current
    StatsCard {
        Text(timeRange(session.startTime, session.endTime), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
        session.location?.let { location ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(location, style = MaterialTheme.typography.bodyMedium, color = StatsSecondaryText, modifier = Modifier.weight(1f))
                TextButton(onClick = { uriHandler.openUri(mapsUrl(location)) }) {
                    Text(stringResource(Res.string.btn_open_in_maps), color = PitchGreen, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text(sessionStatus(session, now), style = MaterialTheme.typography.labelLarge, color = if (session.cancelled) AlertRed else SuccessGreen)
    }
}

/** The week's focus and intensity with the month's focus (design 40f), staff only; amber when unplanned. */
@Composable
private fun WeekStrip(details: TrainingSessionDetails, onOpenWeekPlan: (edit: Boolean) -> Unit) {
    if (!details.canRate) return
    val monday = mondayOf(details.session.date)
    val week = details.weekPlan
    val month = details.monthPlan
    val planned = week != null
    Column(
        Modifier.fillMaxWidth()
            .background(if (planned) DeepCarbon else Amber.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        val fg = if (planned) Color.White else AmberText
        if (week != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.label_this_week, rangeLabel(monday, periodEnd(TrainingPlanLevel.WEEK, monday, details.season))),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White.copy(alpha = 0.6f), modifier = Modifier.weight(1f)
                )
                week.intensity?.let { Text(stringResource(it.chipRes()), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f)) }
            }
            Spacer(Modifier.height(4.dp))
            Text(week.focus.orEmpty(), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = fg)
        } else {
            Text(stringResource(Res.string.msg_week_no_plan), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = fg)
        }
        month?.focus?.let { Text("${monthName(month.periodStart)} · $it", style = MaterialTheme.typography.bodySmall, color = if (planned) Color.White.copy(alpha = 0.7f) else AmberText) }
        if (planned) {
            TextButton(onClick = { onOpenWeekPlan(false) }) { Text(stringResource(Res.string.btn_week_plan), color = Color.White) }
        } else if (details.canEdit) {
            TextButton(onClick = { onOpenWeekPlan(true) }) { Text(stringResource(Res.string.btn_plan_the_week), color = AmberText, fontWeight = FontWeight.Bold) }
        }
    }
}

/**
 * The session's exercises, in order (design 39f): name, description and minutes, with the total.
 * With `MANAGE_TRAINING` and while not cancelled: "Add exercise", a handle to drag each into its place
 * and × to remove from this session (never from the library).
 */
@Composable
private fun ExercisesCard(details: TrainingSessionDetails, enabled: Boolean, onAdd: () -> Unit, onSet: (List<String>) -> Unit) {
    val exercises = details.exercises
    val canChange = details.canEdit && !details.session.cancelled
    val ids = exercises.map { it.id }
    StatsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatsHeading(stringResource(Res.string.label_section_count, stringResource(Res.string.label_exercises), exercises.size), Modifier.weight(1f))
            if (canChange) TextButton(onClick = onAdd, enabled = enabled) { Text(stringResource(Res.string.btn_add_exercise), color = PitchGreen, fontWeight = FontWeight.Bold) }
        }
        if (exercises.isEmpty()) {
            Text(stringResource(Res.string.msg_session_no_exercises), style = MaterialTheme.typography.bodySmall, color = StatsSecondaryText)
            return@StatsCard
        }
        DragReorderColumn(exercises, enabled = canChange && enabled, onMove = { from, to -> onSet(ids.moved(from, to)) }) { i, exercise, handle ->
            Row(Modifier.fillMaxWidth().background(Color.White).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (canChange) DragHandle(handle, enabled)
                Text("${i + 1}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = PitchGreen, modifier = Modifier.padding(end = 10.dp))
                Column(Modifier.weight(1f)) {
                    Text(exercise.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = DeepCarbon)
                    exercise.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = StatsSecondaryText, maxLines = 2) }
                }
                exercise.durationMinutes?.let { Text("$it'", style = MaterialTheme.typography.labelLarge, color = DeepCarbon) }
                if (canChange) {
                    IconButton(onClick = { onSet(ids.filterIndexed { j, _ -> j != i }) }, enabled = enabled) {
                        Icon(painterResource(Res.drawable.ic_close), stringResource(Res.string.cd_remove), tint = DeepCarbon.copy(alpha = 0.5f))
                    }
                }
            }
        }
        val total = exercises.sumOf { it.durationMinutes ?: 0 }
        if (total > 0) Text(stringResource(Res.string.msg_exercises_total, total), style = MaterialTheme.typography.bodySmall, color = StatsSecondaryText)
    }
}

/** Staff only: when ratings open, or how many are rated with "Rate players" (design 39f). */
@Composable
private fun RatingsCard(details: TrainingSessionDetails, now: LocalDateTime, onRatePlayers: () -> Unit) {
    if (!details.canRate) return
    val session = details.session
    StatsCard {
        StatsHeading(stringResource(Res.string.label_player_ratings_staff), Modifier.padding(bottom = 6.dp))
        when {
            session.cancelled -> Text(stringResource(Res.string.msg_ratings_cancelled), style = MaterialTheme.typography.bodySmall, color = StatsSecondaryText)
            !session.ended(now) -> Text(stringResource(Res.string.msg_ratings_open_at, session.endTime.hhmm()), style = MaterialTheme.typography.bodySmall, color = StatsSecondaryText)
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.label_rated_count, session.ratedCount ?: 0, details.poolSize),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = DeepCarbon, modifier = Modifier.weight(1f)
                )
                androidx.compose.material3.Button(
                    onClick = onRatePlayers,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = PitchGreen, contentColor = Color.White)
                ) { Text(stringResource(Res.string.btn_rate_players), fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun CancelledBanner(details: TrainingSessionDetails, canRestore: Boolean, onRestore: () -> Unit) {
    val session = details.session
    val on = session.cancelledAt?.toLocalDateTime(TimeZone.currentSystemDefault())?.date
    Row(
        Modifier.fillMaxWidth().background(CarbonGray.copy(alpha = 0.08f), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            when {
                on != null && session.cancelledByName != null -> stringResource(Res.string.msg_session_cancelled_by, shortDateLabel(on), session.cancelledByName!!)
                on != null -> stringResource(Res.string.msg_session_cancelled, shortDateLabel(on))
                else -> stringResource(Res.string.status_cancelled)
            },
            style = MaterialTheme.typography.bodySmall, color = DeepCarbon, modifier = Modifier.weight(1f)
        )
        if (canRestore) TextButton(onClick = onRestore) { Text(stringResource(Res.string.btn_restore), color = PitchGreen, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun ColumnScope.NotesCard(session: TrainingSession) {
    val notes = session.notes ?: return
    StatsCard {
        StatsHeading(stringResource(Res.string.label_notes_field), Modifier.padding(bottom = 6.dp))
        Text(notes, style = MaterialTheme.typography.bodyMedium, color = DeepCarbon)
    }
}

// ---- Previews ----

private val previewDetails = TrainingSessionDetails(
    session = TrainingSession("s1", "ag1", LocalDate(2026, 10, 15), LocalTime(18, 30), LocalTime(20, 0), location = "Campo do Ribamar", notes = "Bring bibs — small-sided games.", exerciseCount = 3),
    ageGroup = AgeGroup("ag1", "c1", "Sub-15", "s1", 2012, 2012),
    clubName = "CD Ribamar",
    season = previewSeason,
    poolSize = 18,
    weekPlan = TrainingPlan("w", "ag1", TrainingPlanLevel.WEEK, LocalDate(2026, 10, 12), focus = "Playing out against a high press", intensity = TrainingIntensity.MEDIUM),
    monthPlan = TrainingPlan("m", "ag1", TrainingPlanLevel.MONTH, LocalDate(2026, 10, 1), focus = "Building from the back"),
    canEdit = true,
    canRate = true
)

@Preview
@Composable
private fun SessionDetailsPreview() {
    SquadifyTheme { SessionDetailsContent(SessionDetailsUiState(loading = false, details = previewDetails), {}, {}, {}, {}, {}, {}, {}) }
}

@Preview
@Composable
private fun SessionDetailsNoWeekPlanPreview() {
    SquadifyTheme { SessionDetailsContent(SessionDetailsUiState(loading = false, details = previewDetails.copy(weekPlan = null)), {}, {}, {}, {}, {}, {}, {}) }
}

@Preview(widthDp = 900, heightDp = 480)
@Composable
private fun SessionDetailsLandscapePreview() {
    SquadifyTheme { SessionDetailsContent(SessionDetailsUiState(loading = false, details = previewDetails), {}, {}, {}, {}, {}, {}, {}) }
}
