package com.ricardofrango.squadify.presentation.ui.training

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.model.Season
import com.ricardofrango.squadify.model.TrainingPhase
import com.ricardofrango.squadify.model.TrainingPlanLevel
import com.ricardofrango.squadify.model.planFor
import com.ricardofrango.squadify.presentation.ui.components.FormTextField
import com.ricardofrango.squadify.presentation.ui.components.StatsSecondaryText
import com.ricardofrango.squadify.presentation.ui.components.shortDateLabel
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.btn_add_phase
import squadify.app.shared.generated.resources.btn_save_season_plan
import squadify.app.shared.generated.resources.label_focus_field
import squadify.app.shared.generated.resources.label_phase_name
import squadify.app.shared.generated.resources.label_plan_end
import squadify.app.shared.generated.resources.label_plan_objectives
import squadify.app.shared.generated.resources.label_plan_phases
import squadify.app.shared.generated.resources.label_plan_start
import squadify.app.shared.generated.resources.msg_season_range
import squadify.app.shared.generated.resources.title_edit_season_plan

/**
 * Edit season plan (macrocycle, design 40c): objectives, phases (name, start, end, focus) and key dates
 * (a date or a range, and a label). Every date is checked against the season inline; objectives and
 * phases can be reordered. Save is pinned at the bottom.
 */
@Composable
fun SeasonPlanEditScreen(
    ageGroupId: String,
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: PlanEditViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(ageGroupId) { viewModel.start(ageGroupId, TrainingPlanLevel.SEASON, null) }
    LaunchedEffect(viewModel) { viewModel.saved.collect { onSaved() } }
    SeasonPlanEditContent(
        state = state,
        onNavigateBack = onNavigateBack,
        onRetry = { viewModel.start(ageGroupId, TrainingPlanLevel.SEASON, null, force = true) },
        onFormChange = viewModel::onFormChange,
        onSave = viewModel::save
    )
}

@Composable
private fun SeasonPlanEditContent(
    state: PlanEditUiState,
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit,
    onFormChange: (PlanForm) -> Unit,
    onSave: () -> Unit
) {
    val plans = state.plans
    val season = plans?.season
    val form = state.form
    val enabled = !state.saving
    PlanEditorFrame(
        title = stringResource(Res.string.title_edit_season_plan),
        subtitle = plans?.subtitle(),
        state = state,
        saveText = stringResource(Res.string.btn_save_season_plan),
        canSave = season != null && state.dirty && form.isValid(TrainingPlanLevel.SEASON, season) && !state.saving,
        onNavigateBack = onNavigateBack,
        onRetry = onRetry,
        onSave = onSave
    ) { wide ->
        if (season == null) return@PlanEditorFrame
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                stringResource(
                    Res.string.msg_season_range,
                    "${shortDateLabel(season.startDate)} ${season.startDate.year}",
                    "${shortDateLabel(season.endDate)} ${season.endDate.year}"
                ),
                style = MaterialTheme.typography.bodySmall,
                color = StatsSecondaryText
            )
            EditorColumns(
                wide = wide,
                left = {
                    PlanSectionHeading(Res.string.label_plan_objectives)
                    ObjectivesEditor(form.objectives, enabled) { onFormChange(form.copy(objectives = it)) }
                },
                right = {
                    PlanSectionHeading(Res.string.label_plan_phases)
                    form.phases.forEachIndexed { i, phase ->
                        PhaseEditor(phase, season, enabled,
                            onChange = { changed -> onFormChange(form.copy(phases = form.phases.toMutableList().also { it[i] = changed })) },
                            onRemove = { onFormChange(form.copy(phases = form.phases.filterIndexed { j, _ -> j != i })) }
                        )
                    }
                    AddRowButton(stringResource(Res.string.btn_add_phase), enabled) { onFormChange(form.withNewPhase(season)) }
                }
            )
        }
    }
}

@Composable
private fun EditorCard(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.15f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

@Composable
private fun PhaseEditor(
    phase: TrainingPhase,
    season: Season,
    enabled: Boolean,
    onChange: (TrainingPhase) -> Unit,
    onRemove: () -> Unit
) {
    val error = rangeError(phase.startDate, phase.endDate, season)
    EditorCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FormTextField(
                value = phase.name,
                onValueChange = { onChange(phase.copy(name = it)) },
                label = stringResource(Res.string.label_phase_name),
                enabled = enabled,
                keyboardType = KeyboardType.Text,
                modifier = Modifier.weight(1f)
            )
            RemoveRowButton(enabled, onRemove)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PlanDateField(stringResource(Res.string.label_plan_start), phase.startDate, season, enabled,
                error = error.takeIf { it == PlanDateError.BEFORE_SEASON }, modifier = Modifier.weight(1f)) { onChange(phase.copy(startDate = it)) }
            PlanDateField(stringResource(Res.string.label_plan_end), phase.endDate, season, enabled,
                error = error.takeIf { it != PlanDateError.BEFORE_SEASON }, modifier = Modifier.weight(1f)) { onChange(phase.copy(endDate = it)) }
        }
        FormTextField(
            value = phase.focus.orEmpty(),
            onValueChange = { onChange(phase.copy(focus = it)) },
            label = stringResource(Res.string.label_focus_field),
            enabled = enabled,
            keyboardType = KeyboardType.Text
        )
    }
}

@Preview
@Composable
private fun SeasonPlanEditPreview() {
    val form = previewPlans.plans.planFor(TrainingPlanLevel.SEASON, previewSeason.startDate).toForm()
    SquadifyTheme {
        SeasonPlanEditContent(
            PlanEditUiState(loading = false, plans = previewPlans, periodStart = previewSeason.startDate, form = form, original = form),
            {}, {}, {}, {}
        )
    }
}

@Preview(widthDp = 900, heightDp = 480)
@Composable
private fun SeasonPlanEditLandscapePreview() {
    val form = previewPlans.plans.planFor(TrainingPlanLevel.SEASON, previewSeason.startDate).toForm()
    SquadifyTheme {
        SeasonPlanEditContent(
            PlanEditUiState(loading = false, plans = previewPlans, periodStart = previewSeason.startDate, form = form, original = form),
            {}, {}, {}, {}
        )
    }
}
