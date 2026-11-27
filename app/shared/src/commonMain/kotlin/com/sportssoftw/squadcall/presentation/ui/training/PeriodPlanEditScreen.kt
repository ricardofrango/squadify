package com.sportssoftw.squadcall.presentation.ui.training

import com.sportssoftw.squadcall.presentation.ui.components.ConfirmDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sportssoftw.squadcall.model.TrainingIntensity
import com.sportssoftw.squadcall.model.TrainingPlanLevel
import com.sportssoftw.squadcall.model.TrainingPlans
import com.sportssoftw.squadcall.model.monthStartOf
import com.sportssoftw.squadcall.model.monthStarts
import com.sportssoftw.squadcall.model.periodEnd
import com.sportssoftw.squadcall.model.planFor
import com.sportssoftw.squadcall.model.weekStarts
import com.sportssoftw.squadcall.presentation.ui.components.FormTextField
import com.sportssoftw.squadcall.presentation.ui.components.shortDateLabel
import com.sportssoftw.squadcall.presentation.ui.theme.DeepCarbon
import com.sportssoftw.squadcall.presentation.ui.theme.PitchGreen
import com.sportssoftw.squadcall.presentation.ui.theme.SquadcallClubTheme
import com.sportssoftw.squadcall.presentation.ui.theme.SquadcallTheme
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import squadcall.app.shared.generated.resources.Res
import squadcall.app.shared.generated.resources.btn_copy_from
import squadcall.app.shared.generated.resources.btn_keep_editing
import squadcall.app.shared.generated.resources.btn_replace
import squadcall.app.shared.generated.resources.btn_save_month_plan
import squadcall.app.shared.generated.resources.btn_save_week_plan
import squadcall.app.shared.generated.resources.ic_chevron_left
import squadcall.app.shared.generated.resources.ic_chevron_right
import squadcall.app.shared.generated.resources.intensity_chip_high
import squadcall.app.shared.generated.resources.intensity_chip_low
import squadcall.app.shared.generated.resources.intensity_chip_medium
import squadcall.app.shared.generated.resources.intensity_high
import squadcall.app.shared.generated.resources.intensity_low
import squadcall.app.shared.generated.resources.intensity_medium
import squadcall.app.shared.generated.resources.label_focus_placeholder
import squadcall.app.shared.generated.resources.label_focus_required
import squadcall.app.shared.generated.resources.label_intensity
import squadcall.app.shared.generated.resources.label_notes_field
import squadcall.app.shared.generated.resources.label_optional_placeholder
import squadcall.app.shared.generated.resources.label_plan_objectives
import squadcall.app.shared.generated.resources.label_week_of
import squadcall.app.shared.generated.resources.label_week_plan_subtitle
import squadcall.app.shared.generated.resources.msg_replace_typed
import squadcall.app.shared.generated.resources.title_plan_month
import squadcall.app.shared.generated.resources.title_plan_week
import squadcall.app.shared.generated.resources.title_replace_typed

/**
 * The month and week plan editors (design 40d/e): Prev / Next period without leaving (unsaved changes
 * ask first), "Copy from" the previous period (asks before replacing anything typed), Focus (required),
 * objectives, notes, and intensity on a week. The level above sits in the dark strip for context.
 */
@Composable
fun PeriodPlanEditScreen(
    ageGroupId: String,
    level: TrainingPlanLevel,
    periodStart: LocalDate,
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: PlanEditViewModel = koinViewModel()
) {
    SquadcallClubTheme {
        val state by viewModel.state.collectAsStateWithLifecycle()
        LaunchedEffect(ageGroupId) { viewModel.start(ageGroupId, level, periodStart) }
        LaunchedEffect(viewModel) { viewModel.saved.collect { onSaved() } }
        PeriodPlanEditContent(
            state = state,
            level = level,
            onNavigateBack = onNavigateBack,
            onRetry = { viewModel.start(ageGroupId, level, periodStart, force = true) },
            onMoveTo = viewModel::moveTo,
            onFormChange = viewModel::onFormChange,
            onSave = viewModel::save
        )
    }
}

/** The periods of [level] in the season, in order. */
private fun TrainingPlans.periods(level: TrainingPlanLevel): List<LocalDate> =
    if (level == TrainingPlanLevel.MONTH) season.monthStarts() else season.weekStarts()

@Composable
private fun periodLabel(level: TrainingPlanLevel, start: LocalDate): String =
    if (level == TrainingPlanLevel.MONTH) monthName(start) else stringResource(Res.string.label_week_of, shortDateLabel(start))

@Composable
private fun PeriodPlanEditContent(
    state: PlanEditUiState,
    level: TrainingPlanLevel,
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit,
    onMoveTo: (LocalDate) -> Unit,
    onFormChange: (PlanForm) -> Unit,
    onSave: () -> Unit
) {
    val plans = state.plans
    val start = state.periodStart
    val form = state.form
    val enabled = !state.saving
    var pendingMove by remember { mutableStateOf<LocalDate?>(null) }
    var askReplace by remember { mutableStateOf(false) }
    val periods = plans?.periods(level).orEmpty()
    val index = start?.let { periods.indexOf(it) } ?: -1
    val previous = periods.getOrNull(index - 1)
    val next = periods.getOrNull(index + 1)
    val previousPlan = previous?.let { plans?.plans?.planFor(level, it) }
    val move = { to: LocalDate -> if (state.dirty) pendingMove = to else onMoveTo(to) }

    PlanEditorFrame(
        title = when {
            start == null -> ""
            level == TrainingPlanLevel.MONTH -> stringResource(Res.string.title_plan_month, monthName(start))
            else -> stringResource(Res.string.title_plan_week, shortDateLabel(start))
        },
        subtitle = if (level == TrainingPlanLevel.MONTH) plans?.monthSubtitle() else plans?.weekSubtitle(),
        state = state,
        saveText = if (level == TrainingPlanLevel.MONTH && start != null) stringResource(Res.string.btn_save_month_plan, monthName(start)) else stringResource(Res.string.btn_save_week_plan),
        canSave = plans != null && state.dirty && form.isValid(level, plans.season) && !state.saving,
        onNavigateBack = onNavigateBack,
        onRetry = onRetry,
        onSave = onSave,
        contextStrip = {
            if (plans != null && start != null) {
                if (level == TrainingPlanLevel.MONTH) MonthContextStrip(plans, start) else WeekContextStrip(plans, start)
            }
        }
    ) { wide ->
        if (plans == null || start == null) return@PlanEditorFrame
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // No side padding, so the arrows line up with the fields' edges.
            TextButton(onClick = { previous?.let(move) }, enabled = previous != null && enabled, contentPadding = PaddingValues(vertical = 8.dp)) {
                Icon(painterResource(Res.drawable.ic_chevron_left), null, modifier = Modifier.size(18.dp))
                Text(previous?.let { periodLabel(level, it) }.orEmpty(), color = DeepCarbon)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { next?.let(move) }, enabled = next != null && enabled, contentPadding = PaddingValues(vertical = 8.dp)) {
                Text(next?.let { periodLabel(level, it) }.orEmpty(), color = DeepCarbon)
                Icon(painterResource(Res.drawable.ic_chevron_right), null, modifier = Modifier.size(18.dp))
            }
        }
        if (previousPlan != null && previous != null) {
            AddRowButton(stringResource(Res.string.btn_copy_from, periodLabel(level, previous)), enabled) {
                if (form.hasTypedContent()) askReplace = true else onFormChange(form.copiedFrom(previousPlan))
            }
        }
        EditorColumns(
            wide = wide,
            left = {
                FormTextField(
                    value = form.focus,
                    onValueChange = { onFormChange(form.copy(focus = it)) },
                    label = stringResource(Res.string.label_focus_required),
                    placeholder = stringResource(Res.string.label_focus_placeholder),
                    enabled = enabled,
                    keyboardType = KeyboardType.Text
                )
                if (level == TrainingPlanLevel.WEEK) IntensityPicker(form.intensity, enabled) { onFormChange(form.copy(intensity = it)) }
                FormTextField(
                    value = form.notes,
                    onValueChange = { onFormChange(form.copy(notes = it)) },
                    label = stringResource(Res.string.label_notes_field),
                    placeholder = stringResource(Res.string.label_optional_placeholder),
                    enabled = enabled,
                    keyboardType = KeyboardType.Text,
                    singleLine = false,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Default
                )
            },
            right = {
                PlanSectionHeading(Res.string.label_plan_objectives)
                ObjectivesEditor(form.objectives, enabled) { onFormChange(form.copy(objectives = it)) }
            }
        )
    }

    pendingMove?.let { to ->
        DiscardChangesDialog(onDiscard = { pendingMove = null; onMoveTo(to) }, onKeepEditing = { pendingMove = null })
    }
    if (askReplace && previousPlan != null) {
        ConfirmDialog(
            title = stringResource(Res.string.title_replace_typed),
            body = stringResource(Res.string.msg_replace_typed),
            onConfirm = { askReplace = false; onFormChange(form.copiedFrom(previousPlan)) },
            onDismiss = { askReplace = false },
            confirmLabel = stringResource(Res.string.btn_replace),
            dismissLabel = stringResource(Res.string.btn_keep_editing)
        )
    }
}

/** "Week plan · Sub-15 · CD Ribamar". */
@Composable
internal fun TrainingPlans.weekSubtitle(): String = stringResource(Res.string.label_week_plan_subtitle, ageGroup.name, clubName)

/** The month a week belongs to, with its focus, in the dark strip ("OCTOBER · BUILDING FROM THE BACK"). */
@Composable
internal fun WeekContextStrip(plans: TrainingPlans, monday: LocalDate) {
    val month = monthStartOf(monday)
    val focus = plans.plans.planFor(TrainingPlanLevel.MONTH, month)?.focus
    ContextStrip(
        overline = listOfNotNull(monthName(month), focus).joinToString(" · "),
        title = rangeLabel(monday, periodEnd(TrainingPlanLevel.WEEK, monday, plans.season))
    )
}

/** "Medium intensity". */
internal fun TrainingIntensity.chipRes(): StringResource = when (this) {
    TrainingIntensity.LOW -> Res.string.intensity_chip_low
    TrainingIntensity.MEDIUM -> Res.string.intensity_chip_medium
    TrainingIntensity.HIGH -> Res.string.intensity_chip_high
}

/** The label of each intensity. */
internal fun TrainingIntensity.labelRes(): StringResource = when (this) {
    TrainingIntensity.LOW -> Res.string.intensity_low
    TrainingIntensity.MEDIUM -> Res.string.intensity_medium
    TrainingIntensity.HIGH -> Res.string.intensity_high
}

@Composable
private fun IntensityPicker(selected: TrainingIntensity?, enabled: Boolean, onSelected: (TrainingIntensity?) -> Unit) {
    // One child of the editor column, so it gets one gap above and below like the fields.
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.label_intensity), style = MaterialTheme.typography.labelLarge, color = DeepCarbon.copy(alpha = 0.6f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrainingIntensity.entries.forEach { intensity ->
                FilterChip(
                    selected = selected == intensity,
                    // Tapping the selected one clears it: intensity is optional.
                    onClick = { onSelected(if (selected == intensity) null else intensity) },
                    enabled = enabled,
                    label = { Text(stringResource(intensity.labelRes())) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PitchGreen, selectedLabelColor = Color.White)
                )
            }
        }
    }
}

@Preview
@Composable
private fun MonthPlanEditPreview() {
    val month = LocalDate(2026, 11, 1)
    SquadcallTheme {
        PeriodPlanEditContent(
            PlanEditUiState(loading = false, plans = previewPlans, periodStart = month, form = PlanForm(focus = "Pressing triggers", objectives = listOf("Press on the back pass"))),
            TrainingPlanLevel.MONTH, {}, {}, {}, {}, {}
        )
    }
}

@Preview
@Composable
private fun WeekPlanEditPreview() {
    val week = LocalDate(2026, 10, 19)
    SquadcallTheme {
        PeriodPlanEditContent(
            PlanEditUiState(loading = false, plans = previewPlans, periodStart = week, form = PlanForm(focus = "Third-man runs", intensity = TrainingIntensity.MEDIUM)),
            TrainingPlanLevel.WEEK, {}, {}, {}, {}, {}
        )
    }
}

@Preview(widthDp = 900, heightDp = 480)
@Composable
private fun PeriodPlanEditLandscapePreview() {
    val month = LocalDate(2026, 11, 1)
    SquadcallTheme {
        PeriodPlanEditContent(
            PlanEditUiState(loading = false, plans = previewPlans, periodStart = month, form = PlanForm(focus = "Pressing triggers")),
            TrainingPlanLevel.MONTH, {}, {}, {}, {}, {}
        )
    }
}
