package com.ricardofrango.squadify.presentation.ui.training

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.model.TrainingIntensity
import com.ricardofrango.squadify.model.TrainingPlan
import com.ricardofrango.squadify.model.TrainingPlanLevel
import com.ricardofrango.squadify.model.planFor
import com.ricardofrango.squadify.presentation.navigation.collectSettledState
import com.ricardofrango.squadify.presentation.ui.components.FMTopBar
import com.ricardofrango.squadify.presentation.ui.components.StatsCard
import com.ricardofrango.squadify.presentation.ui.components.shortDateLabel
import com.ricardofrango.squadify.presentation.ui.theme.Amber
import com.ricardofrango.squadify.presentation.ui.theme.AmberText
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyClubTheme
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.btn_plan_this_week
import squadify.app.shared.generated.resources.cd_edit_plan
import squadify.app.shared.generated.resources.ic_edit
import squadify.app.shared.generated.resources.label_week_of
import squadify.app.shared.generated.resources.msg_plan_read_only
import squadify.app.shared.generated.resources.msg_week_no_plan
import squadify.app.shared.generated.resources.btn_add_one_session
import squadify.app.shared.generated.resources.label_sessions
import squadify.app.shared.generated.resources.label_section_count
import kotlin.time.Clock
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.plus
import kotlinx.datetime.TimeZone
import kotlinx.datetime.DatePeriod
import com.ricardofrango.squadify.presentation.ui.components.StatsHeading
import com.ricardofrango.squadify.model.TrainingSessions
import androidx.compose.ui.Alignment
import androidx.compose.runtime.getValue
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Row
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Week plan (microcycle, design 40e): the month it belongs to and that month's focus in the dark strip;
 * then the week's focus with its intensity, objectives and notes. The pencil, or "Plan this week" when
 * empty, opens the editor (with `MANAGE_TRAINING`).
 */
@Composable
fun WeekPlanScreen(
    ageGroupId: String,
    monday: LocalDate,
    onNavigateBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenSession: (sessionId: String) -> Unit = {},
    // "Add one session", on a day of this week.
    onAddSession: (date: LocalDate) -> Unit = {},
    refresh: Boolean = false,
    viewModel: TrainingPlansViewModel = koinViewModel()
) {
    SquadifyClubTheme {
        val state = viewModel.state.collectSettledState()
        val sessions by viewModel.sessions.collectAsStateWithLifecycle()
        LaunchedEffect(ageGroupId) { viewModel.load(ageGroupId) }
        LaunchedEffect(refresh) { if (refresh) viewModel.load(ageGroupId, force = true) }
        WeekPlanContent(state, monday, onNavigateBack, onRetry = { viewModel.load(ageGroupId, force = true) }, onEdit, sessions, onOpenSession, onAddSession)
    }
}

/** The week's intensity as a small chip ("Medium intensity"): amber for high, green otherwise. */
@Composable
internal fun IntensityChip(intensity: TrainingIntensity) {
    val (bg, fg) = if (intensity == TrainingIntensity.HIGH) Amber.copy(alpha = 0.2f) to AmberText else PitchGreen.copy(alpha = 0.1f) to PitchGreen
    Text(
        stringResource(intensity.chipRes()),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = fg,
        modifier = Modifier.background(bg, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
private fun WeekPlanContent(
    state: TrainingPlansUiState,
    monday: LocalDate,
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    sessions: TrainingSessions? = null,
    onOpenSession: (String) -> Unit = {},
    onAddSession: (LocalDate) -> Unit = {}
) {
    val plans = (state as? TrainingPlansUiState.Idle)?.plans
    val plan: TrainingPlan? = plans?.plans?.planFor(TrainingPlanLevel.WEEK, monday)
    Scaffold(
        containerColor = Color.White,
        topBar = {
            FMTopBar(
                title = stringResource(Res.string.label_week_of, shortDateLabel(monday)),
                subtitle = plans?.weekSubtitle(),
                onNavigateBack = onNavigateBack,
                actions = {
                    if (plans?.canEdit == true && plan != null) {
                        IconButton(onClick = onEdit) { Icon(painterResource(Res.drawable.ic_edit), stringResource(Res.string.cd_edit_plan), tint = Color.White) }
                    }
                }
            )
        }
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding).background(Color(0xFFF6F6F7))
        if (plans == null) {
            PlanLoadingOrError(state is TrainingPlansUiState.Loading, modifier, onRetry)
            return@Scaffold
        }
        Column(modifier) {
            WeekContextStrip(plans, monday)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (plan == null) {
                    StatsCard {
                        PlanEmpty(
                            message = stringResource(Res.string.msg_week_no_plan),
                            actionText = if (plans.canEdit) stringResource(Res.string.btn_plan_this_week) else null,
                            readOnlyText = stringResource(Res.string.msg_plan_read_only),
                            onAction = onEdit
                        )
                    }
                } else {
                    PlanBody(plan) { plan.intensity?.let { IntensityChip(it) } }
                }
                if (sessions != null) WeekSessions(sessions, monday, onOpenSession, onAddSession)
            }
        }
    }
}

/** The week's sessions (design 40e): tap opens one; "Add one session" on a day of this week. */
@Composable
private fun WeekSessions(sessions: TrainingSessions, monday: LocalDate, onOpenSession: (String) -> Unit, onAddSession: (LocalDate) -> Unit) {
    val sunday = monday.plus(DatePeriod(days = 6))
    val week = sessions.sessions.filter { it.date in monday..sunday }.sortedWith(compareBy({ it.date }, { it.startTime }))
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatsHeading(stringResource(Res.string.label_section_count, stringResource(Res.string.label_sessions), week.size), Modifier.weight(1f))
        if (sessions.canEdit) {
            TextButton(onClick = { onAddSession(now.date.takeIf { it in monday..sunday } ?: monday) }) {
                Text(stringResource(Res.string.btn_add_one_session), color = PitchGreen, fontWeight = FontWeight.Bold)
            }
        }
    }
    week.forEach { session -> SessionRow(session, chipFor(session, now, null, null, false)) { onOpenSession(session.id) } }
}

// ---- Previews ----

private val previewWeekPlans = previewPlans.copy(
    plans = previewPlans.plans + TrainingPlan(
        "p3", "ag1", TrainingPlanLevel.WEEK, LocalDate(2026, 10, 12),
        focus = "Playing out against a high press",
        objectives = listOf("GK as the extra man", "Centre-backs split wide", "Quick switch through the 6"),
        notes = "Saturday's opponent presses high.",
        intensity = TrainingIntensity.MEDIUM
    )
)

@Preview
@Composable
private fun WeekPlanPreview() {
    SquadifyTheme { WeekPlanContent(TrainingPlansUiState.Idle(previewWeekPlans), LocalDate(2026, 10, 12), {}, {}, {}) }
}

@Preview
@Composable
private fun WeekPlanEmptyPreview() {
    SquadifyTheme { WeekPlanContent(TrainingPlansUiState.Idle(previewWeekPlans), LocalDate(2026, 10, 19), {}, {}, {}) }
}

@Preview(widthDp = 900, heightDp = 480)
@Composable
private fun WeekPlanLandscapePreview() {
    SquadifyTheme { WeekPlanContent(TrainingPlansUiState.Idle(previewWeekPlans), LocalDate(2026, 10, 12), {}, {}, {}) }
}
