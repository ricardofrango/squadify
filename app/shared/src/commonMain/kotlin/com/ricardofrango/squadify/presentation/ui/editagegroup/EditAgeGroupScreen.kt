package com.ricardofrango.squadify.presentation.ui.editagegroup

import com.ricardofrango.squadify.presentation.ui.theme.ChromeAccent
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import com.ricardofrango.squadify.presentation.ui.components.submitKeyboardActions
import com.ricardofrango.squadify.presentation.navigation.collectSettledState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.domain.AgeGroupDefaults
import com.ricardofrango.squadify.presentation.ui.components.FMTopBar
import com.ricardofrango.squadify.presentation.ui.createagegroups.AgeGroupSpanType
import com.ricardofrango.squadify.presentation.ui.createagegroups.AgeGroupSpanTypeField
import com.ricardofrango.squadify.presentation.ui.createagegroups.TeamSizeField
import com.ricardofrango.squadify.presentation.ui.createagegroups.YearOfBirthField
import com.ricardofrango.squadify.presentation.ui.theme.AmberText
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyClubTheme
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.btn_retry
import squadify.app.shared.generated.resources.btn_save_changes
import squadify.app.shared.generated.resources.edit_age_group
import squadify.app.shared.generated.resources.edit_age_group_shut_out
import squadify.app.shared.generated.resources.edit_age_group_size_helper
import squadify.app.shared.generated.resources.ic_check
import squadify.app.shared.generated.resources.label_age_group_name_field
import squadify.app.shared.generated.resources.label_age_group_span_type
import squadify.app.shared.generated.resources.label_team_size
import squadify.app.shared.generated.resources.label_year_of_birth
import squadify.app.shared.generated.resources.label_year_of_birth_end
import squadify.app.shared.generated.resources.label_year_of_birth_start
import squadify.app.shared.generated.resources.msg_age_group_conflict
import squadify.app.shared.generated.resources.msg_age_group_range_inverted
import kotlin.time.Clock

/**
 * Edit age group, from Age Group Details' ⋮ (coordinators+): name, birth-year span (one year or two)
 * and the default team size for new teams -- the same fields as Create age groups. The season is fixed
 * (shown as the subtitle). Years used by the season's other age groups are left out of the pickers (and an overlap, e.g. after
 * switching to two years, is flagged and blocks saving), and
 * a span that would leave players already in the pool ineligible is explained in amber and can't be
 * saved (the server enforces both too).
 */
@Composable
fun EditAgeGroupScreen(
    ageGroupId: String,
    onNavigateBack: () -> Unit,
    onAgeGroupUpdated: () -> Unit,
    viewModel: EditAgeGroupViewModel = koinViewModel()
) {
    SquadifyClubTheme {
        val state = viewModel.state.collectSettledState()
        LaunchedEffect(ageGroupId) { viewModel.load(ageGroupId) }
        LaunchedEffect(Unit) { viewModel.saved.collect { onAgeGroupUpdated() } }

        EditAgeGroupContent(
            state = state,
            onNavigateBack = onNavigateBack,
            onRetry = { viewModel.load(ageGroupId) },
            onChange = { transform -> viewModel.updateForm(transform) },
            onSave = { viewModel.save(ageGroupId) }
        )
    }
}

@Composable
private fun EditAgeGroupContent(
    state: EditAgeGroupUiState,
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit,
    onChange: ((AgeGroupFormFields) -> AgeGroupFormFields) -> Unit,
    onSave: () -> Unit
) {
    val idle = state as? EditAgeGroupUiState.Idle
    Scaffold(
        containerColor = Color.White,
        topBar = {
            FMTopBar(
                title = stringResource(Res.string.edit_age_group),
                subtitle = idle?.seasonName,
                onNavigateBack = onNavigateBack,
                actions = {
                    if (idle?.isSubmitting == true) {
                        CircularProgressIndicator(color = ChromeAccent, strokeWidth = 2.dp, modifier = Modifier.padding(end = 16.dp).size(20.dp))
                    } else {
                        IconButton(onClick = onSave, enabled = idle?.canSave == true) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_check),
                                contentDescription = stringResource(Res.string.btn_save_changes),
                                tint = if (idle?.canSave == true) ChromeAccent else Color.White.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding).background(Color.White)
        when (state) {
            EditAgeGroupUiState.Loading -> Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PitchGreen) }
            is EditAgeGroupUiState.Error -> Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(state.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onRetry) { Text(stringResource(Res.string.btn_retry)) }
            }
            is EditAgeGroupUiState.Idle -> EditAgeGroupForm(state, modifier, onChange, onSave)
        }
    }
}

@Composable
private fun EditAgeGroupForm(
    state: EditAgeGroupUiState.Idle,
    modifier: Modifier,
    onChange: ((AgeGroupFormFields) -> AgeGroupFormFields) -> Unit,
    onSave: () -> Unit
) {
    val form = state.form
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    // The usual birth-year range, plus this group's own years in case they fall outside it.
    val allYears = remember(today, state.original) {
        (AgeGroupDefaults.birthYearOptions(today) + state.original.startYear + state.original.endYear).distinct().sortedDescending()
    }
    val freeYears = allYears - state.usedByOthers

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AgeGroupSpanTypeField(
            label = stringResource(Res.string.label_age_group_span_type),
            selectedType = if (form.twoYears) AgeGroupSpanType.TWO_YEAR else AgeGroupSpanType.SINGLE_YEAR,
            onTypeSelected = { type ->
                onChange {
                    val two = type == AgeGroupSpanType.TWO_YEAR
                    // Going to one year keeps the oldest year; going to two adds the next (younger) one.
                    it.copy(twoYears = two, startYear = if (two) it.endYear + 1 else it.endYear)
                }
            }
        )
        if (!form.twoYears) {
            YearOfBirthField(
                label = stringResource(Res.string.label_year_of_birth),
                selectedYear = form.endYear,
                availableYears = freeYears,
                onYearSelected = { year -> onChange { it.copy(startYear = year, endYear = year) } }
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    YearOfBirthField(
                        label = stringResource(Res.string.label_year_of_birth_start),
                        selectedYear = form.startYear,
                        availableYears = freeYears,
                        onYearSelected = { year -> onChange { it.copy(startYear = year) } }
                    )
                }
                Box(Modifier.weight(1f)) {
                    YearOfBirthField(
                        label = stringResource(Res.string.label_year_of_birth_end),
                        selectedYear = form.endYear,
                        availableYears = freeYears,
                        onYearSelected = { year -> onChange { it.copy(endYear = year) } }
                    )
                }
            }
            if (state.inverted) {
                Text(stringResource(Res.string.msg_age_group_range_inverted), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        if (state.collides) {
            Text(stringResource(Res.string.msg_age_group_conflict), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (state.shutOut > 0) {
            Text(
                pluralStringResource(Res.plurals.edit_age_group_shut_out, state.shutOut, state.shutOut, form.endYear),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = AmberText
            )
        }

        OutlinedTextField(
            value = form.name,
            onValueChange = { value -> onChange { it.copy(name = value) } },
            label = { Text(stringResource(Res.string.label_age_group_name_field)) },
            isError = form.name.isBlank(),
            singleLine = true,
            // The only text field: the keyboard key saves, like the top bar's check.
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = submitKeyboardActions { if (state.canSave) onSave() },
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
                selectedTeamSize = form.teamSize,
                options = state.teamSizeOptions,
                onTeamSizeSelected = { size -> onChange { it.copy(teamSize = size) } }
            )
            Text(
                stringResource(Res.string.edit_age_group_size_helper),
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

@Preview
@Composable
private fun EditAgeGroupPreview() {
    SquadifyTheme {
        val fields = AgeGroupFormFields("Sub-15", twoYears = false, startYear = 2011, endYear = 2011, teamSize = 11)
        EditAgeGroupContent(
            state = EditAgeGroupUiState.Idle("2025/26", listOf(4, 5, 7, 9, 11), setOf(2012, 2013), listOf(2011, 2011, 2012), fields, fields),
            onNavigateBack = {}, onRetry = {}, onChange = {}, onSave = {}
        )
    }
}

@Preview
@Composable
private fun EditAgeGroupShutOutPreview() {
    SquadifyTheme {
        val fields = AgeGroupFormFields("Sub-15", twoYears = true, startYear = 2011, endYear = 2010, teamSize = 11)
        EditAgeGroupContent(
            state = EditAgeGroupUiState.Idle("2025/26", listOf(4, 5, 7, 9, 11), setOf(2012), listOf(2010, 2011, 2011), fields, fields.copy(twoYears = false, endYear = 2011)),
            onNavigateBack = {}, onRetry = {}, onChange = {}, onSave = {}
        )
    }
}
