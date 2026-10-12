package com.ricardofrango.squadify.presentation.ui.training

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.TrainingExercise
import com.ricardofrango.squadify.model.TrainingSession
import com.ricardofrango.squadify.model.TrainingSessionDetails
import com.ricardofrango.squadify.presentation.ui.components.FMTopBar
import com.ricardofrango.squadify.presentation.ui.components.FormErrorLine
import com.ricardofrango.squadify.presentation.ui.components.FormTextField
import com.ricardofrango.squadify.presentation.ui.components.PrimaryLoadingButton
import com.ricardofrango.squadify.presentation.ui.components.StatsHeading
import com.ricardofrango.squadify.presentation.ui.components.StatsSecondaryText
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.btn_add_n_exercises
import squadify.app.shared.generated.resources.btn_new
import squadify.app.shared.generated.resources.btn_save_and_add
import squadify.app.shared.generated.resources.btn_save_changes
import squadify.app.shared.generated.resources.btn_saving_changes
import squadify.app.shared.generated.resources.cd_clear_search
import squadify.app.shared.generated.resources.cd_edit_exercise
import squadify.app.shared.generated.resources.ic_add
import squadify.app.shared.generated.resources.ic_close
import squadify.app.shared.generated.resources.ic_edit
import squadify.app.shared.generated.resources.ic_search
import squadify.app.shared.generated.resources.label_duration
import squadify.app.shared.generated.resources.label_exercise_description
import squadify.app.shared.generated.resources.label_exercise_description_placeholder
import squadify.app.shared.generated.resources.label_exercise_name
import squadify.app.shared.generated.resources.label_library
import squadify.app.shared.generated.resources.label_library_subtitle
import squadify.app.shared.generated.resources.label_minutes
import squadify.app.shared.generated.resources.label_objectives
import squadify.app.shared.generated.resources.label_objectives_placeholder
import squadify.app.shared.generated.resources.label_search_exercises
import squadify.app.shared.generated.resources.label_section_count
import squadify.app.shared.generated.resources.label_used_n
import squadify.app.shared.generated.resources.msg_library_empty
import squadify.app.shared.generated.resources.msg_library_no_match
import squadify.app.shared.generated.resources.msg_used_in_sessions
import squadify.app.shared.generated.resources.title_add_exercises
import squadify.app.shared.generated.resources.title_edit_exercise
import squadify.app.shared.generated.resources.title_new_exercise

/**
 * Add exercises (design 39h): the age group's library, most used first, searchable by name and
 * description, multi-select added in the order picked. "New" creates an exercise and adds it straight
 * away; the pencil on a row edits it in the library, which changes it everywhere it's used.
 */
@Composable
fun ExerciseLibraryScreen(
    sessionId: String,
    onNavigateBack: () -> Unit,
    onAdded: () -> Unit,
    viewModel: ExerciseLibraryViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(sessionId) { viewModel.start(sessionId) }
    LaunchedEffect(viewModel) { viewModel.done.collect { onAdded() } }
    ExerciseLibraryContent(
        state = state,
        onNavigateBack = { if (state.form != null) viewModel.closeForm() else onNavigateBack() },
        onRetry = { viewModel.start(sessionId, force = true) },
        onQueryChange = viewModel::onQueryChange,
        onToggle = viewModel::toggle,
        onNew = viewModel::newExercise,
        onEdit = viewModel::edit,
        onFormChange = viewModel::onFormChange,
        onSaveForm = viewModel::saveForm,
        onAddSelected = viewModel::addSelected
    )
}

@Composable
private fun ExerciseLibraryContent(
    state: ExerciseLibraryUiState,
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit,
    onQueryChange: (String) -> Unit,
    onToggle: (String) -> Unit,
    onNew: () -> Unit,
    onEdit: (TrainingExercise) -> Unit,
    onFormChange: (ExerciseForm) -> Unit,
    onSaveForm: () -> Unit,
    onAddSelected: () -> Unit
) {
    val session = state.session
    val form = state.form
    val ageGroupName = session?.ageGroup?.name.orEmpty()
    Scaffold(
        containerColor = Color.White,
        topBar = {
            FMTopBar(
                title = stringResource(
                    when {
                        form == null -> Res.string.title_add_exercises
                        state.editing != null -> Res.string.title_edit_exercise
                        else -> Res.string.title_new_exercise
                    }
                ),
                subtitle = if (state.editing != null) stringResource(Res.string.label_library_subtitle, ageGroupName) else session?.let { "${it.ageGroup.name} · ${it.clubName}" },
                onNavigateBack = onNavigateBack,
                actions = {
                    if (form == null && session != null) {
                        TextButton(onClick = onNew) {
                            Icon(painterResource(Res.drawable.ic_add), null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(Res.string.btn_new), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
        }
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().background(Color.White)
        if (session == null) {
            PlanLoadingOrError(state.loading, modifier, onRetry)
            return@Scaffold
        }
        Column(modifier) {
            if (form != null) {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.editing?.let { Text(stringResource(Res.string.msg_used_in_sessions, it.usedCount), style = MaterialTheme.typography.bodySmall, color = StatsSecondaryText) }
                    ExerciseFormFields(form, !state.saving, onFormChange)
                }
            } else {
                LibraryList(state, onQueryChange, onToggle, onEdit, Modifier.weight(1f))
            }
            FormErrorLine(state.error)
            Surface(color = Color.White, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), horizontalAlignment = Alignment.End) {
                    if (form != null) {
                        PrimaryLoadingButton(
                            text = stringResource(if (state.editing != null) Res.string.btn_save_changes else Res.string.btn_save_and_add),
                            submittingText = stringResource(Res.string.btn_saving_changes),
                            isLoading = state.saving,
                            enabled = form.valid && !state.saving && (state.editing == null || form != state.editing.toForm()),
                            onClick = onSaveForm,
                            modifier = Modifier.widthIn(max = 420.dp)
                        )
                    } else {
                        PrimaryLoadingButton(
                            text = stringResource(Res.string.btn_add_n_exercises, state.selected.size),
                            submittingText = stringResource(Res.string.btn_saving_changes),
                            isLoading = state.saving,
                            enabled = state.selected.isNotEmpty() && !state.saving,
                            onClick = onAddSelected,
                            modifier = Modifier.widthIn(max = 420.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryList(
    state: ExerciseLibraryUiState,
    onQueryChange: (String) -> Unit,
    onToggle: (String) -> Unit,
    onEdit: (TrainingExercise) -> Unit,
    modifier: Modifier
) {
    val session = state.session ?: return
    val inSession = session.exercises.map { it.id }.toSet()
    val shown = filterLibrary(state.library, state.query, inSession)
    Column(modifier) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(Res.string.label_search_exercises)) },
            leadingIcon = { Icon(painterResource(Res.drawable.ic_search), null, tint = DeepCarbon.copy(alpha = 0.45f)) },
            trailingIcon = {
                if (state.query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) {
                    Icon(painterResource(Res.drawable.ic_close), stringResource(Res.string.cd_clear_search), tint = DeepCarbon.copy(alpha = 0.55f))
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PitchGreen, unfocusedBorderColor = CarbonGray.copy(alpha = 0.3f), focusedTextColor = DeepCarbon, unfocusedTextColor = DeepCarbon),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            item {
                StatsHeading(
                    stringResource(Res.string.label_section_count, stringResource(Res.string.label_library, session.ageGroup.name.uppercase()), state.library.size),
                    Modifier.padding(bottom = 6.dp)
                )
            }
            if (shown.isEmpty()) {
                item {
                    Text(
                        if (state.query.isBlank()) stringResource(Res.string.msg_library_empty) else stringResource(Res.string.msg_library_no_match, state.query.trim()),
                        style = MaterialTheme.typography.bodyMedium, color = StatsSecondaryText, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(24.dp)
                    )
                }
            }
            items(shown, key = { it.id }) { exercise ->
                val selected = exercise.id in state.selected
                Row(
                    Modifier.fillMaxWidth().clickable { onToggle(exercise.id) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = selected, onCheckedChange = { onToggle(exercise.id) }, colors = CheckboxDefaults.colors(checkedColor = PitchGreen))
                    Column(Modifier.weight(1f)) {
                        Text(exercise.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = DeepCarbon)
                        exercise.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = StatsSecondaryText, maxLines = 2) }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        exercise.durationMinutes?.let { Text("$it'", style = MaterialTheme.typography.labelLarge, color = DeepCarbon) }
                        Text(stringResource(Res.string.label_used_n, exercise.usedCount), style = MaterialTheme.typography.labelSmall, color = StatsSecondaryText)
                    }
                    if (session.canEdit) {
                        IconButton(onClick = { onEdit(exercise) }) {
                            Icon(painterResource(Res.drawable.ic_edit), stringResource(Res.string.cd_edit_exercise), tint = DeepCarbon.copy(alpha = 0.45f), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseFormFields(form: ExerciseForm, enabled: Boolean, onChange: (ExerciseForm) -> Unit) {
    FormTextField(
        value = form.name,
        onValueChange = { onChange(form.copy(name = it)) },
        label = stringResource(Res.string.label_exercise_name),
        enabled = enabled,
        keyboardType = KeyboardType.Text
    )
    FormTextField(
        value = form.description,
        onValueChange = { onChange(form.copy(description = it)) },
        label = stringResource(Res.string.label_exercise_description),
        placeholder = stringResource(Res.string.label_exercise_description_placeholder),
        enabled = enabled,
        keyboardType = KeyboardType.Text,
        singleLine = false,
        imeAction = ImeAction.Default
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        FormTextField(
            value = form.duration,
            onValueChange = { v -> onChange(form.copy(duration = v.filter { it.isDigit() }.take(3))) },
            label = stringResource(Res.string.label_duration),
            enabled = enabled,
            keyboardType = KeyboardType.Number,
            modifier = Modifier.width(140.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(stringResource(Res.string.label_minutes), style = MaterialTheme.typography.bodyMedium, color = StatsSecondaryText)
    }
    FormTextField(
        value = form.objectives,
        onValueChange = { onChange(form.copy(objectives = it)) },
        label = stringResource(Res.string.label_objectives),
        placeholder = stringResource(Res.string.label_objectives_placeholder),
        enabled = enabled,
        keyboardType = KeyboardType.Text,
        imeAction = ImeAction.Done
    )
}

// ---- Previews ----

private val previewLibraryState = ExerciseLibraryUiState(
    loading = false,
    session = TrainingSessionDetails(
        session = TrainingSession("s1", "ag1", LocalDate(2026, 10, 15), LocalTime(18, 30), LocalTime(20, 0)),
        ageGroup = AgeGroup("ag1", "c1", "Sub-15", "s1", 2012, 2012),
        clubName = "CD Ribamar", season = previewSeason, poolSize = 18, canEdit = true, canRate = true
    ),
    library = listOf(
        TrainingExercise("e1", "ag1", "Rondo 4v1", "Possession in a 10×10 square, two-touch", 15, usedCount = 14),
        TrainingExercise("e2", "ag1", "Possession 6v3", "Three zones, switch the play", 15, usedCount = 11),
        TrainingExercise("e3", "ag1", "Box to box sprints", "Recovery runs, 6 reps", 8, usedCount = 3)
    ),
    selected = listOf("e2")
)

@Preview
@Composable
private fun ExerciseLibraryPreview() {
    SquadifyTheme { ExerciseLibraryContent(previewLibraryState, {}, {}, {}, {}, {}, {}, {}, {}, {}) }
}

@Preview
@Composable
private fun NewExercisePreview() {
    SquadifyTheme { ExerciseLibraryContent(previewLibraryState.copy(form = ExerciseForm(name = "Pressing triggers", duration = "20")), {}, {}, {}, {}, {}, {}, {}, {}, {}) }
}

@Preview(widthDp = 900, heightDp = 480)
@Composable
private fun ExerciseLibraryLandscapePreview() {
    SquadifyTheme { ExerciseLibraryContent(previewLibraryState, {}, {}, {}, {}, {}, {}, {}, {}, {}) }
}
