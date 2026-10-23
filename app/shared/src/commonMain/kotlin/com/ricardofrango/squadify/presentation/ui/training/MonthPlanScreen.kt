package com.ricardofrango.squadify.presentation.ui.training

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.model.TrainingPlan
import com.ricardofrango.squadify.model.TrainingPlanLevel
import com.ricardofrango.squadify.model.TrainingPlans
import com.ricardofrango.squadify.model.periodEnd
import com.ricardofrango.squadify.model.planFor
import com.ricardofrango.squadify.model.weeksOfMonth
import com.ricardofrango.squadify.presentation.navigation.collectSettledState
import com.ricardofrango.squadify.presentation.ui.components.SquadifyTopBar
import com.ricardofrango.squadify.presentation.ui.components.StatsCard
import com.ricardofrango.squadify.presentation.ui.components.StatsSecondaryText
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyClubTheme
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import com.ricardofrango.squadify.presentation.ui.theme.screenContentPadding
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.cd_edit_plan
import squadify.app.shared.generated.resources.ic_edit
import squadify.app.shared.generated.resources.ic_expand_less
import squadify.app.shared.generated.resources.ic_expand_more
import squadify.app.shared.generated.resources.label_month_plan_subtitle
import squadify.app.shared.generated.resources.label_phase_strip
import squadify.app.shared.generated.resources.label_plan_focus
import squadify.app.shared.generated.resources.label_plan_notes
import squadify.app.shared.generated.resources.label_plan_objectives
import squadify.app.shared.generated.resources.label_season_objectives
import squadify.app.shared.generated.resources.label_weeks_planned
import squadify.app.shared.generated.resources.msg_month_no_plan
import squadify.app.shared.generated.resources.msg_plan_read_only
import squadify.app.shared.generated.resources.plan_no_focus
import squadify.app.shared.generated.resources.title_plan_month

/**
 * Month plan (mesocycle, design 40d): the phase it sits in (and the season objectives, collapsed) in the
 * dark strip; its focus, objectives and notes; then its weeks with their status and focus (tap opens
 * the week). The pencil, or "Plan {month}" when empty, opens the editor (with `MANAGE_TRAINING`).
 */
@Composable
fun MonthPlanScreen(
    ageGroupId: String,
    month: LocalDate,
    onNavigateBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenWeek: (monday: LocalDate) -> Unit,
    refresh: Boolean = false,
    viewModel: TrainingPlansViewModel = koinViewModel()
) {
    SquadifyClubTheme {
        val state = viewModel.state.collectSettledState()
        LaunchedEffect(ageGroupId) { viewModel.load(ageGroupId) }
        LaunchedEffect(refresh) { if (refresh) viewModel.load(ageGroupId, force = true) }
        MonthPlanContent(state, month, onNavigateBack, onRetry = { viewModel.load(ageGroupId, force = true) }, onEdit, onOpenWeek)
    }
}

/** "Month plan · Sub-15 · CD Ribamar". */
@Composable
internal fun TrainingPlans.monthSubtitle(): String = stringResource(Res.string.label_month_plan_subtitle, ageGroup.name, clubName)

@Composable
private fun MonthPlanContent(
    state: TrainingPlansUiState,
    month: LocalDate,
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onOpenWeek: (LocalDate) -> Unit
) {
    val plans = (state as? TrainingPlansUiState.Idle)?.plans
    val plan = plans?.plans?.planFor(TrainingPlanLevel.MONTH, month)
    Scaffold(
        containerColor = Color.White,
        topBar = {
            SquadifyTopBar(
                title = "${monthName(month)} ${month.year}",
                subtitle = plans?.monthSubtitle(),
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
            MonthContextStrip(plans, month)
            BoxWithConstraints(Modifier.weight(1f)) {
                val wide = maxWidth >= 600.dp
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).screenContentPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val planPart: @Composable () -> Unit = {
                        if (plan == null) {
                            StatsCard {
                                PlanEmpty(
                                    message = stringResource(Res.string.msg_month_no_plan, monthName(month)),
                                    actionText = if (plans.canEdit) stringResource(Res.string.title_plan_month, monthName(month)) else null,
                                    readOnlyText = stringResource(Res.string.msg_plan_read_only),
                                    onAction = onEdit
                                )
                            }
                        } else {
                            PlanBody(plan)
                        }
                    }
                    if (wide) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) { planPart() }
                            Column(Modifier.weight(1f)) { WeeksCard(plans, month, onOpenWeek) }
                        }
                    } else {
                        planPart()
                        WeeksCard(plans, month, onOpenWeek)
                    }
                }
            }
        }
    }
}

/** The phase the month sits in, with the season objectives collapsed under it. */
@Composable
internal fun MonthContextStrip(plans: TrainingPlans, month: LocalDate) {
    val seasonPlan = plans.plans.planFor(TrainingPlanLevel.SEASON, plans.season.startDate)
    val phases = phasesOfMonth(seasonPlan?.phases.orEmpty(), month, plans.season)
    val phase = phases.firstOrNull() ?: return
    var expanded by remember { mutableStateOf(false) }
    ContextStrip(
        overline = "${stringResource(Res.string.label_phase_strip, phase.name)} · ${rangeLabel(phase.startDate, phase.endDate)}",
        title = phase.focus
    ) {
        val objectives = seasonPlan?.objectives.orEmpty()
        if (objectives.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.label_season_objectives, objectives.size), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                Icon(painterResource(if (expanded) Res.drawable.ic_expand_less else Res.drawable.ic_expand_more), null, tint = Color.White.copy(alpha = 0.7f))
            }
            if (expanded) objectives.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = Color.White) }
        }
    }
}

/** Focus, objectives and notes of a month or week plan. [focusTrailing] holds the week's intensity chip. */
@Composable
internal fun PlanBody(plan: TrainingPlan, focusTrailing: @Composable () -> Unit = {}) {
    StatsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlanSectionHeading(Res.string.label_plan_focus, modifier = Modifier.weight(1f))
            focusTrailing()
        }
        Spacer(Modifier.height(6.dp))
        Text(plan.focus.orEmpty(), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
    }
    if (plan.objectives.isNotEmpty()) {
        StatsCard {
            PlanSectionHeading(Res.string.label_plan_objectives, plan.objectives.size, Modifier.padding(bottom = 10.dp))
            ObjectiveRows(plan.objectives)
        }
    }
    plan.notes?.let { notes ->
        StatsCard {
            PlanSectionHeading(Res.string.label_plan_notes, modifier = Modifier.padding(bottom = 6.dp))
            Text(notes, style = MaterialTheme.typography.bodyMedium, color = DeepCarbon)
        }
    }
}

@Composable
private fun WeeksCard(plans: TrainingPlans, month: LocalDate, onOpenWeek: (LocalDate) -> Unit) {
    val weeks = weeksOfMonth(plans.season, month)
    val planned = weeks.count { plans.plans.planFor(TrainingPlanLevel.WEEK, it) != null }
    StatsCard {
        Text(
            stringResource(Res.string.label_weeks_planned, planned, weeks.size),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = StatsSecondaryText,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        weeks.forEach { monday ->
            val week = plans.plans.planFor(TrainingPlanLevel.WEEK, monday)
            Row(Modifier.fillMaxWidth().clickable { onOpenWeek(monday) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(rangeLabel(monday, periodEnd(TrainingPlanLevel.WEEK, monday, plans.season)), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = DeepCarbon)
                    Text(
                        week?.focus ?: stringResource(Res.string.plan_no_focus),
                        style = MaterialTheme.typography.bodySmall,
                        color = StatsSecondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                PlanStatusChip(week != null)
            }
        }
    }
}

// ---- Previews ----

@Preview
@Composable
private fun MonthPlanPreview() {
    SquadifyTheme { MonthPlanContent(TrainingPlansUiState.Idle(previewPlans), LocalDate(2026, 10, 1), {}, {}, {}, {}) }
}

@Preview
@Composable
private fun MonthPlanEmptyPreview() {
    SquadifyTheme { MonthPlanContent(TrainingPlansUiState.Idle(previewPlans), LocalDate(2026, 11, 1), {}, {}, {}, {}) }
}

@Preview(widthDp = 900, heightDp = 480)
@Composable
private fun MonthPlanLandscapePreview() {
    SquadifyTheme { MonthPlanContent(TrainingPlansUiState.Idle(previewPlans), LocalDate(2026, 10, 1), {}, {}, {}, {}) }
}
