package com.ricardofrango.squadify.presentation.ui.training

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.model.TrainingPlanLevel
import com.ricardofrango.squadify.model.TrainingPlans
import com.ricardofrango.squadify.model.monthStartOf
import com.ricardofrango.squadify.model.monthStarts
import com.ricardofrango.squadify.model.mondayOf
import com.ricardofrango.squadify.model.periodEnd
import com.ricardofrango.squadify.model.planFor
import com.ricardofrango.squadify.model.weekStarts
import com.ricardofrango.squadify.presentation.ui.components.StatsCard
import com.ricardofrango.squadify.presentation.ui.components.StatsHeading
import com.ricardofrango.squadify.presentation.ui.components.StatsSecondaryText
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.btn_create_season_plan
import squadify.app.shared.generated.resources.btn_open
import squadify.app.shared.generated.resources.btn_plan_this_week
import squadify.app.shared.generated.resources.cd_next
import squadify.app.shared.generated.resources.cd_previous
import squadify.app.shared.generated.resources.ic_chevron_left
import squadify.app.shared.generated.resources.ic_chevron_right
import squadify.app.shared.generated.resources.label_season_plan_card
import squadify.app.shared.generated.resources.label_this_month
import squadify.app.shared.generated.resources.label_this_week
import squadify.app.shared.generated.resources.msg_month_no_plan
import squadify.app.shared.generated.resources.msg_no_season_plan
import squadify.app.shared.generated.resources.msg_plan_read_only
import squadify.app.shared.generated.resources.msg_week_no_plan
import squadify.app.shared.generated.resources.title_plan_month

// The top of the Training tab (design 40a/b), the way a coach plans: Season plan → This month → This
// week, each a summary that opens its own screen, and an empty card with its Plan button otherwise.

/** [date] clamped into [periods] (the first period on or before it), for opening "this month / week". */
internal fun currentPeriod(periods: List<LocalDate>, date: LocalDate): LocalDate? =
    periods.lastOrNull { it <= date } ?: periods.firstOrNull()

/** How far through the season [today] is, 0..100. */
internal fun seasonProgress(plans: TrainingPlans, today: LocalDate): Int {
    val total = plans.season.startDate.daysUntil(plans.season.endDate).coerceAtLeast(1)
    return (plans.season.startDate.daysUntil(today) * 100 / total).coerceIn(0, 100)
}

@Composable
fun SeasonPlanCard(plans: TrainingPlans, today: LocalDate, onOpen: () -> Unit, onEdit: () -> Unit) {
    val plan = plans.plans.planFor(TrainingPlanLevel.SEASON, plans.season.startDate)
    StatsCard(Modifier.clickable(enabled = plan != null, onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatsHeading(stringResource(Res.string.label_season_plan_card, plans.season.name), Modifier.weight(1f))
            if (plan != null) TextButton(onClick = onOpen) { Text(stringResource(Res.string.btn_open), color = PitchGreen) }
        }
        if (plan == null) {
            Spacer(Modifier.height(8.dp))
            PlanEmpty(
                message = stringResource(Res.string.msg_no_season_plan),
                actionText = if (plans.canEdit) stringResource(Res.string.btn_create_season_plan) else null,
                readOnlyText = stringResource(Res.string.msg_plan_read_only),
                onAction = onEdit
            )
            return@StatsCard
        }
        Spacer(Modifier.height(6.dp))
        ObjectiveRows(plan.objectives.take(3))
        val phase = plan.phases.firstOrNull { today >= it.startDate && today <= it.endDate }
        val progress = seasonProgress(plans, today)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                phase?.let { "${it.name} · ${rangeLabel(it.startDate, it.endDate)}" }.orEmpty(),
                style = MaterialTheme.typography.bodySmall, color = StatsSecondaryText, modifier = Modifier.weight(1f)
            )
            Text("$progress%", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).background(CarbonGray.copy(alpha = 0.08f), RoundedCornerShape(3.dp))) {
            Box(Modifier.fillMaxWidth(progress / 100f).height(6.dp).background(PitchGreen, RoundedCornerShape(3.dp)))
        }
    }
}

/** ‹ title › of the month and week cards. */
@Composable
private fun PeriodHeader(title: String, hasPrevious: Boolean, hasNext: Boolean, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatsHeading(title, Modifier.weight(1f))
        IconButton(onClick = onPrevious, enabled = hasPrevious, modifier = Modifier.size(32.dp)) {
            Icon(painterResource(Res.drawable.ic_chevron_left), stringResource(Res.string.cd_previous), tint = DeepCarbon.copy(alpha = if (hasPrevious) 0.7f else 0.2f))
        }
        IconButton(onClick = onNext, enabled = hasNext, modifier = Modifier.size(32.dp)) {
            Icon(painterResource(Res.drawable.ic_chevron_right), stringResource(Res.string.cd_next), tint = DeepCarbon.copy(alpha = if (hasNext) 0.7f else 0.2f))
        }
    }
}

@Composable
fun MonthPlanCard(plans: TrainingPlans, month: LocalDate, onMove: (LocalDate) -> Unit, onOpen: () -> Unit, onEdit: () -> Unit) {
    val months = plans.season.monthStarts()
    val i = months.indexOf(month)
    val plan = plans.plans.planFor(TrainingPlanLevel.MONTH, month)
    val phase = phasesOfMonth(plans.plans.planFor(TrainingPlanLevel.SEASON, plans.season.startDate)?.phases.orEmpty(), month, plans.season).firstOrNull()
    StatsCard(Modifier.clickable(onClick = onOpen)) {
        PeriodHeader(
            stringResource(Res.string.label_this_month, monthName(month)),
            hasPrevious = i > 0, hasNext = i < months.size - 1,
            onPrevious = { onMove(months[i - 1]) }, onNext = { onMove(months[i + 1]) }
        )
        if (plan == null) {
            PlanEmpty(
                message = stringResource(Res.string.msg_month_no_plan, monthName(month)),
                actionText = if (plans.canEdit) stringResource(Res.string.title_plan_month, monthName(month)) else null,
                readOnlyText = stringResource(Res.string.msg_plan_read_only),
                onAction = onEdit
            )
            return@StatsCard
        }
        phase?.let { Text(it.name, style = MaterialTheme.typography.labelMedium, color = StatsSecondaryText) }
        Text(plan.focus.orEmpty(), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
        if (plan.objectives.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            ObjectiveRows(plan.objectives)
        }
    }
}

@Composable
fun WeekPlanCard(plans: TrainingPlans, monday: LocalDate, onMove: (LocalDate) -> Unit, onOpen: () -> Unit, onEdit: () -> Unit) {
    val weeks = plans.season.weekStarts()
    val i = weeks.indexOf(monday)
    val plan = plans.plans.planFor(TrainingPlanLevel.WEEK, monday)
    StatsCard(Modifier.clickable(onClick = onOpen)) {
        PeriodHeader(
            stringResource(Res.string.label_this_week, rangeLabel(monday, periodEnd(TrainingPlanLevel.WEEK, monday, plans.season))),
            hasPrevious = i > 0, hasNext = i < weeks.size - 1,
            onPrevious = { onMove(weeks[i - 1]) }, onNext = { onMove(weeks[i + 1]) }
        )
        if (plan == null) {
            PlanEmpty(
                message = stringResource(Res.string.msg_week_no_plan),
                actionText = if (plans.canEdit) stringResource(Res.string.btn_plan_this_week) else null,
                readOnlyText = stringResource(Res.string.msg_plan_read_only),
                onAction = onEdit
            )
            return@StatsCard
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(plan.focus.orEmpty(), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon, modifier = Modifier.weight(1f))
            plan.intensity?.let { Spacer(Modifier.width(8.dp)); IntensityChip(it) }
        }
        if (plan.objectives.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            ObjectiveRows(plan.objectives)
        }
    }
}

/** The month and week the Training tab opens on: today's, kept inside the season. */
internal fun initialMonth(plans: TrainingPlans, today: LocalDate): LocalDate? =
    currentPeriod(plans.season.monthStarts(), monthStartOf(today))

internal fun initialWeek(plans: TrainingPlans, today: LocalDate): LocalDate? =
    currentPeriod(plans.season.weekStarts(), mondayOf(today))
