package com.ricardofrango.squadify.presentation.ui.editteam

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.presentation.ui.components.FMTopBar
import com.ricardofrango.squadify.presentation.ui.createagegroups.TeamSizeField
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.btn_retry
import squadify.app.shared.generated.resources.btn_save_changes
import squadify.app.shared.generated.resources.edit_team_size_helper
import squadify.app.shared.generated.resources.ic_check
import squadify.app.shared.generated.resources.label_edit_team
import squadify.app.shared.generated.resources.label_team_name_field
import squadify.app.shared.generated.resources.label_team_size

/**
 * Edit team, from Team Details' ⋮ (coordinators, or this team's coach): the name and the team size,
 * with the ✓ save in the header like Edit Player / Club settings. The age group and season are fixed
 * -- a team belongs to them -- so they're shown as the subtitle, not as fields.
 */
@Composable
fun EditTeamScreen(
    teamId: String,
    onNavigateBack: () -> Unit,
    onTeamUpdated: () -> Unit,
    viewModel: EditTeamViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(teamId) { viewModel.load(teamId) }
    LaunchedEffect(Unit) { viewModel.saved.collect { onTeamUpdated() } }

    EditTeamContent(
        state = state,
        onNavigateBack = onNavigateBack,
        onRetry = { viewModel.load(teamId) },
        onNameChange = { value -> viewModel.updateForm { it.copy(name = value) } },
        onTeamSizeSelected = { value -> viewModel.updateForm { it.copy(teamSize = value) } },
        onSave = { viewModel.save(teamId) }
    )
}

@Composable
private fun EditTeamContent(
    state: EditTeamUiState,
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit,
    onNameChange: (String) -> Unit,
    onTeamSizeSelected: (Int) -> Unit,
    onSave: () -> Unit
) {
    val idle = state as? EditTeamUiState.Idle
    Scaffold(
        containerColor = Color.White,
        topBar = {
            FMTopBar(
                title = stringResource(Res.string.label_edit_team),
                subtitle = idle?.let { "${it.ageGroupName} · ${it.seasonName}" },
                onNavigateBack = onNavigateBack,
                actions = {
                    if (idle?.isSubmitting == true) {
                        CircularProgressIndicator(color = PitchGreen, strokeWidth = 2.dp, modifier = Modifier.padding(end = 16.dp).size(20.dp))
                    } else {
                        IconButton(onClick = onSave, enabled = idle?.canSave == true) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_check),
                                contentDescription = stringResource(Res.string.btn_save_changes),
                                tint = if (idle?.canSave == true) PitchGreen else Color.White.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding).background(Color.White)
        when (state) {
            EditTeamUiState.Loading -> Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PitchGreen) }
            is EditTeamUiState.Error -> Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(state.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onRetry) { Text(stringResource(Res.string.btn_retry)) }
            }
            is EditTeamUiState.Idle -> Column(
                modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = state.form.name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(Res.string.label_team_name_field)) },
                    isError = state.form.name.isBlank(),
                    singleLine = true,
                    enabled = !state.isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DeepCarbon, unfocusedTextColor = DeepCarbon,
                        focusedBorderColor = PitchGreen, unfocusedBorderColor = CarbonGray.copy(alpha = 0.3f),
                        focusedLabelColor = PitchGreen, unfocusedLabelColor = CarbonGray
                    )
                )
                Column {
                    TeamSizeField(
                        label = stringResource(Res.string.label_team_size),
                        selectedTeamSize = state.form.teamSize,
                        options = state.teamSizeOptions,
                        onTeamSizeSelected = onTeamSizeSelected
                    )
                    Text(
                        stringResource(Res.string.edit_team_size_helper),
                        Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = DeepCarbon.copy(alpha = 0.5f)
                    )
                }
                if (state.submitError != null) {
                    Text(state.submitError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Preview
@Composable
private fun EditTeamPreview() {
    SquadifyTheme {
        val fields = TeamFormFields("Sub-15 B", 7)
        EditTeamContent(
            state = EditTeamUiState.Idle("Sub-15", "2025/26", listOf(4, 5, 7, 9, 11), fields, fields.copy(teamSize = 9)),
            onNavigateBack = {}, onRetry = {}, onNameChange = {}, onTeamSizeSelected = {}, onSave = {}
        )
    }
}
