package com.ricardofrango.squadify.presentation.ui.training

import com.ricardofrango.squadify.presentation.ui.components.ErrorScreen
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.model.Season
import com.ricardofrango.squadify.model.TrainingPlans
import com.ricardofrango.squadify.presentation.ui.components.DatePickerSheet
import com.ricardofrango.squadify.presentation.ui.components.FormTextField
import com.ricardofrango.squadify.presentation.ui.components.StatsHeading
import com.ricardofrango.squadify.presentation.ui.components.StatsSecondaryText
import com.ricardofrango.squadify.presentation.ui.components.shortDateLabel
import com.ricardofrango.squadify.presentation.ui.theme.AlertRed
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.btn_add_objective
import squadify.app.shared.generated.resources.btn_discard
import squadify.app.shared.generated.resources.btn_keep_editing
import squadify.app.shared.generated.resources.btn_retry
import squadify.app.shared.generated.resources.cd_remove
import squadify.app.shared.generated.resources.ic_add
import squadify.app.shared.generated.resources.ic_close
import squadify.app.shared.generated.resources.label_new_objective
import squadify.app.shared.generated.resources.label_pick_date
import squadify.app.shared.generated.resources.label_section_count
import squadify.app.shared.generated.resources.month_full_1
import squadify.app.shared.generated.resources.month_full_10
import squadify.app.shared.generated.resources.month_full_11
import squadify.app.shared.generated.resources.month_full_12
import squadify.app.shared.generated.resources.month_full_2
import squadify.app.shared.generated.resources.month_full_3
import squadify.app.shared.generated.resources.month_full_4
import squadify.app.shared.generated.resources.month_full_5
import squadify.app.shared.generated.resources.month_full_6
import squadify.app.shared.generated.resources.month_full_7
import squadify.app.shared.generated.resources.month_full_8
import squadify.app.shared.generated.resources.month_full_9
import squadify.app.shared.generated.resources.msg_check_connection
import squadify.app.shared.generated.resources.msg_discard_plan_changes
import squadify.app.shared.generated.resources.msg_end_before_start
import squadify.app.shared.generated.resources.msg_must_end_by
import squadify.app.shared.generated.resources.msg_must_start_from
import squadify.app.shared.generated.resources.msg_plans_load_failed
import squadify.app.shared.generated.resources.plan_not_planned
import squadify.app.shared.generated.resources.plan_planned
import squadify.app.shared.generated.resources.title_discard_changes

// Pieces shared by the season, month and week plan screens (design 40).

private val MonthFull: List<StringResource> = listOf(
    Res.string.month_full_1, Res.string.month_full_2, Res.string.month_full_3, Res.string.month_full_4,
    Res.string.month_full_5, Res.string.month_full_6, Res.string.month_full_7, Res.string.month_full_8,
    Res.string.month_full_9, Res.string.month_full_10, Res.string.month_full_11, Res.string.month_full_12
)

/** "October". */
@Composable
fun monthName(date: LocalDate): String = stringResource(MonthFull[date.month.ordinal])

/** "11 Oct", "1 – 27 Sep" or "28 Sep – 20 Dec". */
@Composable
fun rangeLabel(start: LocalDate, end: LocalDate?): String = when {
    end == null || end == start -> shortDateLabel(start)
    start.year == end.year && start.month == end.month -> "${start.day} – ${shortDateLabel(end)}"
    else -> "${shortDateLabel(start)} – ${shortDateLabel(end)}"
}

/** "Sub-15 · 26/27 · CD Ribamar", the plan screens' subtitle. */
fun TrainingPlans.subtitle(): String = "${ageGroup.name} · ${season.name} · $clubName"

/** "OBJECTIVES · 3". */
@Composable
fun PlanSectionHeading(label: StringResource, count: Int? = null, modifier: Modifier = Modifier) {
    val text = stringResource(label)
    StatsHeading(if (count != null) stringResource(Res.string.label_section_count, text, count) else text, modifier)
}

/** The objectives as rows with a green dot. */
@Composable
fun ObjectiveRows(objectives: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        objectives.forEach { objective ->
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 7.dp).size(7.dp).background(PitchGreen, CircleShape))
                Spacer(Modifier.width(10.dp))
                Text(objective, style = MaterialTheme.typography.bodyMedium, color = DeepCarbon)
            }
        }
    }
}

/** Planned ✓ / Not planned. */
@Composable
fun PlanStatusChip(planned: Boolean) {
    Text(
        stringResource(if (planned) Res.string.plan_planned else Res.string.plan_not_planned),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = if (planned) PitchGreen else DeepCarbon.copy(alpha = 0.5f),
        modifier = Modifier
            .background(if (planned) PitchGreen.copy(alpha = 0.1f) else CarbonGray.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

/** The dark strip at the top of a plan screen with the level above it, for context (design 40d/e). */
@Composable
fun ContextStrip(overline: String, title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit = {}) {
    Column(
        modifier.fillMaxWidth().background(DeepCarbon).padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Text(overline.uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.White.copy(alpha = 0.6f))
        if (title != null) {
            Spacer(Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
        }
        content()
    }
}

/** An empty plan: what's missing, and the button to add it, or who can (design 40b). */
@Composable
fun PlanEmpty(message: String, actionText: String?, readOnlyText: String, onAction: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(message, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
        if (actionText != null) {
            Button(onClick = onAction, colors = ButtonDefaults.buttonColors(containerColor = PitchGreen, contentColor = Color.White), shape = RoundedCornerShape(12.dp)) {
                Text(actionText, fontWeight = FontWeight.Bold)
            }
        } else {
            Text(readOnlyText, style = MaterialTheme.typography.bodySmall, color = StatsSecondaryText)
        }
    }
}

/** The loading and error states of a plan screen. */
@Composable
fun PlanLoadingOrError(loading: Boolean, modifier: Modifier, onRetry: () -> Unit) {
    if (loading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PitchGreen) }
    } else {
        ErrorScreen(title = stringResource(Res.string.msg_plans_load_failed), onRetry = onRetry, modifier = modifier.fillMaxSize())
    }
}

/** The objectives as editable multi-line fields: remove, and "Add objective". */
@Composable
fun ObjectivesEditor(objectives: List<String>, enabled: Boolean, onChange: (List<String>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        objectives.forEachIndexed { i, objective ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                FormTextField(
                    value = objective,
                    onValueChange = { value -> onChange(objectives.toMutableList().also { it[i] = value }) },
                    label = stringResource(Res.string.label_new_objective),
                    enabled = enabled,
                    keyboardType = KeyboardType.Text,
                    modifier = Modifier.weight(1f),
                    singleLine = false,
                    minLines = 1,
                    imeAction = ImeAction.Default
                )
                RemoveRowButton(enabled) { onChange(objectives.filterIndexed { j, _ -> j != i }) }
            }
        }
        AddRowButton(stringResource(Res.string.btn_add_objective), enabled) { onChange(objectives + "") }
    }
}

/** × to remove a row, compact so it sits close to the screen's edge (the 48dp default left a wide gap). */
@Composable
fun RemoveRowButton(enabled: Boolean, onRemove: () -> Unit) {
    IconButton(onClick = onRemove, enabled = enabled, modifier = Modifier.padding(start = 4.dp).size(32.dp)) {
        Icon(painterResource(Res.drawable.ic_close), stringResource(Res.string.cd_remove), tint = DeepCarbon.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
    }
}

/** "+ Add objective" style button. */
@Composable
fun AddRowButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled) {
        Icon(painterResource(Res.drawable.ic_add), null, tint = PitchGreen, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = PitchGreen, fontWeight = FontWeight.Bold)
    }
}

/** A date that opens a picker, with the error under it when it's out of the season. */
@Composable
fun PlanDateField(label: String, date: LocalDate?, season: Season, enabled: Boolean, error: PlanDateError?, modifier: Modifier = Modifier, onPick: (LocalDate) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Column(modifier) {
        Surface(
            onClick = { picking = true },
            enabled = enabled,
            shape = RoundedCornerShape(14.dp),
            color = Color.White,
            border = BorderStroke(1.dp, if (error != null) AlertRed else CarbonGray.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = DeepCarbon.copy(alpha = 0.5f))
                Text(date?.let { "${shortDateLabel(it)} ${it.year}" } ?: "—", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = DeepCarbon)
            }
        }
        if (error != null) {
            Text(
                when (error) {
                    PlanDateError.BEFORE_SEASON -> stringResource(Res.string.msg_must_start_from, "${shortDateLabel(season.startDate)} ${season.startDate.year}")
                    PlanDateError.AFTER_SEASON -> stringResource(Res.string.msg_must_end_by, "${shortDateLabel(season.endDate)} ${season.endDate.year}")
                    PlanDateError.END_BEFORE_START -> stringResource(Res.string.msg_end_before_start)
                },
                style = MaterialTheme.typography.bodySmall,
                color = AlertRed,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
    if (picking) {
        DatePickerSheet(
            title = Res.string.label_pick_date,
            initialDate = date ?: season.startDate,
            onConfirm = { picking = false; onPick(it) },
            onDismiss = { picking = false }
        )
    }
}

/** "Discard changes?" when leaving an editor (or moving to another period) with unsaved changes. */
@Composable
fun DiscardChangesDialog(onDiscard: () -> Unit, onKeepEditing: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        title = { Text(stringResource(Res.string.title_discard_changes)) },
        text = { Text(stringResource(Res.string.msg_discard_plan_changes)) },
        confirmButton = { TextButton(onClick = onDiscard) { Text(stringResource(Res.string.btn_discard), color = AlertRed, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onKeepEditing) { Text(stringResource(Res.string.btn_keep_editing), color = MaterialTheme.colorScheme.onSurfaceVariant) } }
    )
}
