package com.ricardofrango.squadify.presentation.ui.training

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.Season
import com.ricardofrango.squadify.model.SeasonType
import com.ricardofrango.squadify.model.TrainingPhase
import com.ricardofrango.squadify.model.TrainingPlan
import com.ricardofrango.squadify.model.TrainingPlanLevel
import com.ricardofrango.squadify.model.TrainingPlans
import com.ricardofrango.squadify.model.monthStarts
import com.ricardofrango.squadify.model.periodEnd
import com.ricardofrango.squadify.model.planFor
import com.ricardofrango.squadify.presentation.navigation.collectSettledState
import com.ricardofrango.squadify.presentation.ui.components.FMTopBar
import com.ricardofrango.squadify.presentation.ui.components.StatsCard
import com.ricardofrango.squadify.presentation.ui.components.StatsSecondaryText
import com.ricardofrango.squadify.presentation.ui.theme.AlertRed
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.btn_create_season_plan
import squadify.app.shared.generated.resources.cd_edit_plan
import squadify.app.shared.generated.resources.ic_edit
import squadify.app.shared.generated.resources.label_plan_months
import squadify.app.shared.generated.resources.label_plan_objectives
import squadify.app.shared.generated.resources.label_plan_phases
import squadify.app.shared.generated.resources.label_timeline_today
import squadify.app.shared.generated.resources.msg_no_season_plan
import squadify.app.shared.generated.resources.msg_plan_read_only
import squadify.app.shared.generated.resources.title_season_plan
import kotlin.time.Clock

/**
 * Season plan (macrocycle, design 40c): a timeline of the season with the phases as bands, key dates
 * as flags and today as a line; the months with their status (tap opens the month plan); then the
 * objectives, phases and key dates. The pencil (with `MANAGE_TRAINING`) opens the editor.
 */
@Composable
fun SeasonPlanScreen(
    ageGroupId: String,
    onNavigateBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenMonth: (monthStart: LocalDate) -> Unit,
    // True right after an editor saved: reload.
    refresh: Boolean = false,
    viewModel: TrainingPlansViewModel = koinViewModel()
) {
    val state = viewModel.state.collectSettledState()
    LaunchedEffect(ageGroupId) { viewModel.load(ageGroupId) }
    LaunchedEffect(refresh) { if (refresh) viewModel.load(ageGroupId, force = true) }
    SeasonPlanContent(state, onNavigateBack, onRetry = { viewModel.load(ageGroupId, force = true) }, onEdit, onOpenMonth)
}

@Composable
private fun SeasonPlanContent(
    state: TrainingPlansUiState,
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onOpenMonth: (LocalDate) -> Unit
) {
    val plans = (state as? TrainingPlansUiState.Idle)?.plans
    val plan = plans?.plans?.planFor(TrainingPlanLevel.SEASON, plans.season.startDate)
    Scaffold(
        containerColor = Color.White,
        topBar = {
            FMTopBar(
                title = stringResource(Res.string.title_season_plan),
                subtitle = plans?.subtitle(),
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
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        BoxWithConstraints(modifier) {
            val wide = maxWidth >= 600.dp
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StatsCard { SeasonTimeline(plans.season, plan?.phases.orEmpty(), today) }
                if (plan == null) {
                    StatsCard {
                        PlanEmpty(
                            message = stringResource(Res.string.msg_no_season_plan),
                            actionText = if (plans.canEdit) stringResource(Res.string.btn_create_season_plan) else null,
                            readOnlyText = stringResource(Res.string.msg_plan_read_only),
                            onAction = onEdit
                        )
                    }
                }
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) { if (plan != null) ObjectivesCard(plan) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) { if (plan != null) PhasesCard(plan) }
                        Column(Modifier.weight(1f)) { MonthsCard(plans, plan, onOpenMonth) }
                    }
                } else {
                    MonthsCard(plans, plan, onOpenMonth)
                    if (plan != null) {
                        ObjectivesCard(plan)
                        PhasesCard(plan)
                    }
                }
            }
        }
    }
}

private val PhaseColors = listOf(
    Color(0xFF9CCC65), PitchGreen, CarbonGray.copy(alpha = 0.25f), Color(0xFF43A047), Color(0xFF1B5E20)
)

/** Where [date] sits along the season, 0..1. */
private fun fraction(season: Season, date: LocalDate): Float {
    val total = season.startDate.daysUntil(season.endDate).coerceAtLeast(1)
    return (season.startDate.daysUntil(date).toFloat() / total).coerceIn(0f, 1f)
}

/** The season from start to end: month initials, phase bands and today (design 40c). */
@Composable
private fun SeasonTimeline(season: Season, phases: List<TrainingPhase>, today: LocalDate) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = maxWidth
        Column {
            // Month initials, each placed where its month starts.
            Box(Modifier.fillMaxWidth().height(16.dp)) {
                season.monthStarts().forEach { month ->
                    val x = width * fraction(season, maxOf(month, season.startDate))
                    Text(monthName(month).take(1), style = MaterialTheme.typography.labelSmall, color = StatsSecondaryText, modifier = Modifier.offset(x = x))
                }
            }
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(28.dp).background(CarbonGray.copy(alpha = 0.05f), RoundedCornerShape(6.dp))) {
                phases.forEachIndexed { i, phase ->
                    val start = fraction(season, phase.startDate)
                    val end = fraction(season, phase.endDate)
                    Box(
                        Modifier
                            .offset(x = width * start)
                            .width(width * (end - start).coerceAtLeast(0.01f))
                            .fillMaxHeight()
                            .background(PhaseColors[i % PhaseColors.size], RoundedCornerShape(4.dp))
                    )
                }
                if (today >= season.startDate && today <= season.endDate) {
                    Box(Modifier.offset(x = width * fraction(season, today)).width(2.dp).fillMaxHeight().background(AlertRed))
                }
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                phases.forEachIndexed { i, phase -> Legend(phase.name, PhaseColors[i % PhaseColors.size]) }
                if (today >= season.startDate && today <= season.endDate) Legend(stringResource(Res.string.label_timeline_today), AlertRed)
            }
        }
    }
}

@Composable
private fun Legend(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(3.dp)))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = StatsSecondaryText)
    }
}

/** The phases that overlap the month starting on [monthStart]. */
internal fun phasesOfMonth(phases: List<TrainingPhase>, monthStart: LocalDate, season: Season): List<TrainingPhase> {
    val monthEnd = periodEnd(TrainingPlanLevel.MONTH, monthStart, season)
    return phases.filter { it.startDate <= monthEnd && it.endDate >= monthStart }
}

@Composable
private fun MonthsCard(plans: TrainingPlans, plan: TrainingPlan?, onOpenMonth: (LocalDate) -> Unit) {
    StatsCard {
        PlanSectionHeading(Res.string.label_plan_months, modifier = Modifier.padding(bottom = 6.dp))
        plans.season.monthStarts().forEach { month ->
            val phases = phasesOfMonth(plan?.phases.orEmpty(), month, plans.season)
            Row(
                Modifier.fillMaxWidth().clickable { onOpenMonth(month) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(monthName(month), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = DeepCarbon)
                    if (phases.isNotEmpty()) {
                        Text(phases.joinToString(" · ") { it.name }, style = MaterialTheme.typography.bodySmall, color = StatsSecondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                PlanStatusChip(plans.plans.planFor(TrainingPlanLevel.MONTH, month) != null)
            }
        }
    }
}

@Composable
private fun ObjectivesCard(plan: TrainingPlan) {
    if (plan.objectives.isEmpty()) return
    StatsCard {
        PlanSectionHeading(Res.string.label_plan_objectives, plan.objectives.size, Modifier.padding(bottom = 10.dp))
        ObjectiveRows(plan.objectives)
    }
}

@Composable
private fun PhasesCard(plan: TrainingPlan) {
    if (plan.phases.isEmpty()) return
    StatsCard {
        PlanSectionHeading(Res.string.label_plan_phases, plan.phases.size, Modifier.padding(bottom = 10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            plan.phases.forEachIndexed { i, phase ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 5.dp).size(10.dp).background(PhaseColors[i % PhaseColors.size], RoundedCornerShape(3.dp)))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("${phase.name}  ·  ${rangeLabel(phase.startDate, phase.endDate)}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
                        phase.focus?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = StatsSecondaryText) }
                    }
                }
            }
        }
    }
}

// ---- Previews ----

internal val previewSeason = Season("s1", "c1", "26/27", SeasonType.EUROPEAN_YEAR, LocalDate(2026, 9, 1), LocalDate(2027, 6, 30), true)

internal val previewPlans = TrainingPlans(
    ageGroup = AgeGroup("ag1", "c1", "Sub-15", "s1", 2012, 2012),
    clubName = "CD Ribamar",
    season = previewSeason,
    plans = listOf(
        TrainingPlan(
            "p1", "ag1", TrainingPlanLevel.SEASON, previewSeason.startDate,
            objectives = listOf("Build from the back under pressure", "Every player gets 50%+ of available minutes"),
            phases = listOf(
                TrainingPhase("Pre-season", LocalDate(2026, 9, 1), LocalDate(2026, 9, 27), "Fitness base, team shape"),
                TrainingPhase("1st half", LocalDate(2026, 9, 28), LocalDate(2026, 12, 20), "Consolidate the 1-4-3-3"),
                TrainingPhase("Break", LocalDate(2026, 12, 21), LocalDate(2027, 1, 4)),
                TrainingPhase("2nd half", LocalDate(2027, 1, 5), LocalDate(2027, 4, 25), "Pressing and transitions")
            )
        ),
        TrainingPlan("p2", "ag1", TrainingPlanLevel.MONTH, LocalDate(2026, 10, 1), focus = "Building from the back")
    ),
    canEdit = true
)

@Preview
@Composable
private fun SeasonPlanPreview() {
    SquadifyTheme { SeasonPlanContent(TrainingPlansUiState.Idle(previewPlans), {}, {}, {}, {}) }
}

@Preview
@Composable
private fun SeasonPlanEmptyPreview() {
    SquadifyTheme { SeasonPlanContent(TrainingPlansUiState.Idle(previewPlans.copy(plans = emptyList())), {}, {}, {}, {}) }
}

@Preview(widthDp = 900, heightDp = 480)
@Composable
private fun SeasonPlanLandscapePreview() {
    SquadifyTheme { SeasonPlanContent(TrainingPlansUiState.Idle(previewPlans), {}, {}, {}, {}) }
}
