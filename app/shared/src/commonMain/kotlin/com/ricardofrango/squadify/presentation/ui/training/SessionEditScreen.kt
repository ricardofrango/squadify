package com.ricardofrango.squadify.presentation.ui.training

import com.ricardofrango.squadify.presentation.ui.components.ScreenWindowInsets
import com.ricardofrango.squadify.presentation.ui.components.isWide
import com.ricardofrango.squadify.presentation.ui.theme.ScreenBackground
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.ReplacedSession
import com.ricardofrango.squadify.model.TrainingSessions
import com.ricardofrango.squadify.presentation.ui.components.SquadifyTopBar
import com.ricardofrango.squadify.presentation.ui.components.FormErrorLine
import com.ricardofrango.squadify.presentation.ui.components.FormTextField
import com.ricardofrango.squadify.presentation.ui.components.PrimaryLoadingButton
import com.ricardofrango.squadify.presentation.ui.components.StatsHeading
import com.ricardofrango.squadify.presentation.ui.theme.AlertRed
import com.ricardofrango.squadify.presentation.ui.theme.AmberText
import com.ricardofrango.squadify.presentation.ui.theme.ScreenPadding
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyClubTheme
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import com.ricardofrango.squadify.presentation.ui.theme.screenContentPadding
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.btn_add_session
import squadify.app.shared.generated.resources.btn_save_changes
import squadify.app.shared.generated.resources.btn_saving_changes
import squadify.app.shared.generated.resources.label_date_required
import squadify.app.shared.generated.resources.label_end_required
import squadify.app.shared.generated.resources.label_location_field
import squadify.app.shared.generated.resources.label_notes_field
import squadify.app.shared.generated.resources.label_opponent_notes_section
import squadify.app.shared.generated.resources.label_session_notes_placeholder
import squadify.app.shared.generated.resources.label_start_required
import squadify.app.shared.generated.resources.label_when
import squadify.app.shared.generated.resources.label_where
import squadify.app.shared.generated.resources.msg_end_before_start
import squadify.app.shared.generated.resources.msg_replaces_session
import squadify.app.shared.generated.resources.title_add_session
import squadify.app.shared.generated.resources.title_edit_session

/**
 * Add one session, or edit one (design 39e): date, start and end (end after start), location (the club's
 * stadium by default) and notes. A session already at that date and start time is flagged inline, since
 * saving replaces it. Edit enables Save only after a change.
 */
@Composable
fun SessionEditScreen(
    ageGroupId: String,
    sessionId: String?,
    date: LocalDate?,
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: SessionEditViewModel = koinViewModel()
) {
    SquadifyClubTheme {
        val state by viewModel.state.collectAsStateWithLifecycle()
        LaunchedEffect(ageGroupId, sessionId) { viewModel.start(ageGroupId, sessionId, date) }
        LaunchedEffect(viewModel) { viewModel.saved.collect { onSaved() } }
        SessionEditContent(
            state = state,
            onNavigateBack = onNavigateBack,
            onRetry = { viewModel.start(ageGroupId, sessionId, date, force = true) },
            onFormChange = viewModel::onFormChange,
            onSave = viewModel::save
        )
    }
}

@Composable
private fun SessionEditContent(
    state: SessionEditUiState,
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit,
    onFormChange: (SessionForm) -> Unit,
    onSave: () -> Unit
) {
    val editing = state.editingId != null
    Scaffold(
        contentWindowInsets = ScreenWindowInsets,
        containerColor = ScreenBackground,
        topBar = {
            SquadifyTopBar(
                title = stringResource(if (editing) Res.string.title_edit_session else Res.string.title_add_session),
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
        val form = state.form
        val enabled = !state.saving
        Column(modifier) {
            BoxWithConstraints(Modifier.weight(1f)) {
                val wide = isWide
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).screenContentPadding()) {
                    EditorColumns(
                        wide = wide,
                        left = {
                            StatsHeading(stringResource(Res.string.label_when))
                            PlanDateField(stringResource(Res.string.label_date_required), form.date, sessions.season, enabled, error = null) { onFormChange(form.copy(date = it)) }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                TimeField(stringResource(Res.string.label_start_required), form.start, enabled, Modifier.weight(1f)) { onFormChange(form.copy(start = it)) }
                                TimeField(stringResource(Res.string.label_end_required), form.end, enabled, Modifier.weight(1f)) { onFormChange(form.copy(end = it)) }
                            }
                            if (form.end <= form.start) Text(stringResource(Res.string.msg_end_before_start), style = MaterialTheme.typography.bodySmall, color = AlertRed)
                            form.clash(sessions.sessions, state.editingId)?.let { clash ->
                                Text(stringResource(Res.string.msg_replaces_session), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = AmberText)
                                replacedContent(ReplacedSession(clash.id, clash.exerciseCount, clash.ratedCount ?: 0))?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = AlertRed)
                                }
                            }
                        },
                        right = {
                            StatsHeading(stringResource(Res.string.label_where))
                            FormTextField(
                                value = form.location,
                                onValueChange = { onFormChange(form.copy(location = it)) },
                                label = stringResource(Res.string.label_location_field),
                                enabled = enabled,
                                keyboardType = KeyboardType.Text
                            )
                            StatsHeading(stringResource(Res.string.label_opponent_notes_section))
                            FormTextField(
                                value = form.notes,
                                onValueChange = { onFormChange(form.copy(notes = it)) },
                                label = stringResource(Res.string.label_notes_field),
                                placeholder = stringResource(Res.string.label_session_notes_placeholder),
                                enabled = enabled,
                                keyboardType = KeyboardType.Text,
                                singleLine = false,
                                imeAction = ImeAction.Default
                            )
                        }
                    )
                    FormErrorLine(state.error)
                }
            }
            Surface(color = Color.White, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = ScreenPadding.Horizontal, vertical = 14.dp)) {
                    PrimaryLoadingButton(
                        text = stringResource(if (editing) Res.string.btn_save_changes else Res.string.btn_add_session),
                        submittingText = stringResource(Res.string.btn_saving_changes),
                        isLoading = state.saving,
                        enabled = state.canSave,
                        onClick = onSave
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun AddSessionPreview() {
    val sessions = TrainingSessions(AgeGroup("ag1", "c1", "Sub-15", "s1", 2012, 2012), "CD Ribamar", previewSeason, "Campo do Ribamar", sessions = emptyList(), canEdit = true, canRate = true)
    SquadifyTheme {
        SessionEditContent(SessionEditUiState(loading = false, sessions = sessions, form = SessionForm(date = LocalDate(2026, 10, 17), location = "Campo do Ribamar")), {}, {}, {}, {})
    }
}

@Preview(widthDp = 900, heightDp = 420)
@Composable
private fun AddSessionLandscapePreview() {
    val sessions = TrainingSessions(AgeGroup("ag1", "c1", "Sub-15", "s1", 2012, 2012), "CD Ribamar", previewSeason, "Campo do Ribamar", sessions = emptyList(), canEdit = true, canRate = true)
    SquadifyTheme {
        SessionEditContent(SessionEditUiState(loading = false, sessions = sessions, form = SessionForm(date = LocalDate(2026, 10, 17), location = "Campo do Ribamar")), {}, {}, {}, {})
    }
}
