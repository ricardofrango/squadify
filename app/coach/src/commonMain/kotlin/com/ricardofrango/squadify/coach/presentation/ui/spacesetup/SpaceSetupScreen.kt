package com.ricardofrango.squadify.coach.presentation.ui.spacesetup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricardofrango.squadify.domain.AgeGroupDefaults
import com.ricardofrango.squadify.model.SportType
import com.ricardofrango.squadify.presentation.ui.components.FormErrorLine
import com.ricardofrango.squadify.presentation.ui.components.FormIntro
import com.ricardofrango.squadify.presentation.ui.components.FormTextField
import com.ricardofrango.squadify.presentation.ui.components.PinnedBottomBar
import com.ricardofrango.squadify.presentation.ui.components.PrimaryLoadingButton
import com.ricardofrango.squadify.presentation.ui.components.SectionCard
import com.ricardofrango.squadify.presentation.ui.components.SegmentedFilter
import com.ricardofrango.squadify.presentation.ui.components.SquadifyTopBar
import com.ricardofrango.squadify.presentation.ui.components.readableWidth
import com.ricardofrango.squadify.presentation.ui.createagegroups.AgeGroupSpanType
import com.ricardofrango.squadify.presentation.ui.createagegroups.AgeGroupSpanTypeField
import com.ricardofrango.squadify.presentation.ui.createagegroups.TeamSizeField
import com.ricardofrango.squadify.presentation.ui.createagegroups.YearOfBirthField
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.ScreenBackground
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import com.ricardofrango.squadify.presentation.ui.theme.screenContentPadding
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import squadify.app.coach.generated.resources.Res
import squadify.app.coach.generated.resources.btn_space_setup_create
import squadify.app.coach.generated.resources.btn_space_setup_creating
import squadify.app.coach.generated.resources.msg_space_setup_failed
import squadify.app.coach.generated.resources.space_setup_intro
import squadify.app.coach.generated.resources.space_setup_name
import squadify.app.coach.generated.resources.space_setup_name_supporting
import squadify.app.coach.generated.resources.space_setup_season
import squadify.app.coach.generated.resources.space_setup_section_space
import squadify.app.coach.generated.resources.space_setup_section_team
import squadify.app.coach.generated.resources.space_setup_sport
import squadify.app.coach.generated.resources.space_setup_team_name
import squadify.app.coach.generated.resources.space_setup_team_name_supporting
import squadify.app.coach.generated.resources.space_setup_title
import squadify.app.shared.generated.resources.btn_logout
import squadify.app.shared.generated.resources.label_age_group_name_field
import squadify.app.shared.generated.resources.label_age_group_section
import squadify.app.shared.generated.resources.label_age_group_span_type
import squadify.app.shared.generated.resources.label_name_placeholder_pick_year_first
import squadify.app.shared.generated.resources.label_team_size
import squadify.app.shared.generated.resources.label_year_of_birth
import squadify.app.shared.generated.resources.label_year_of_birth_end
import squadify.app.shared.generated.resources.label_year_of_birth_start
import squadify.app.shared.generated.resources.msg_age_group_range_inverted
import squadify.app.shared.generated.resources.sport_football
import squadify.app.shared.generated.resources.sport_futsal
import org.koin.compose.viewmodel.koinViewModel
import squadify.app.shared.generated.resources.Res as SharedRes

/**
 * The first setup (REQUIREMENTS §5c), right after signing up: the space's name and sport, its first
 * age group and its first team, in one form and one request. The season is the one running today,
 * shown but never asked for. It's the root of the back stack, so there's no back arrow; signing out is
 * the way out.
 */
@Composable
fun SpaceSetupScreen(
    onSetUp: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: SpaceSetupViewModel = koinViewModel()
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                SpaceSetupNavigationEvent.NavigateToHome -> onSetUp()
                SpaceSetupNavigationEvent.NavigateToLogin -> onSignedOut()
            }
        }
    }

    SpaceSetupContent(
        uiState = uiState,
        today = viewModel.today,
        seasonEndYear = viewModel.seasonEndYear,
        onFormChanged = viewModel::onFormChanged,
        onCreate = viewModel::onCreateClicked,
        onSignOut = viewModel::onSignOutClicked
    )
}

@Composable
private fun SpaceSetupContent(
    uiState: SpaceSetupUiState,
    today: LocalDate,
    seasonEndYear: Int,
    onFormChanged: (SpaceSetupForm) -> Unit,
    onCreate: () -> Unit,
    onSignOut: () -> Unit
) {
    val form = uiState.form
    val enabled = !uiState.isSubmitting
    val yearOptions = remember(today) { AgeGroupDefaults.birthYearOptions(today) }
    val inferredName = form.inferredAgeGroupName(seasonEndYear)

    Scaffold(
        containerColor = ScreenBackground,
        topBar = {
            SquadifyTopBar(
                title = stringResource(Res.string.space_setup_title),
                showBackButton = false,
                actions = {
                    TextButton(onClick = onSignOut, enabled = enabled) {
                        Text(stringResource(SharedRes.string.btn_logout), color = Color.White)
                    }
                }
            )
        },
        bottomBar = {
            PinnedBottomBar {
                if (uiState.failed) FormErrorLine(stringResource(Res.string.msg_space_setup_failed))
                PrimaryLoadingButton(
                    text = Res.string.btn_space_setup_create,
                    submittingText = Res.string.btn_space_setup_creating,
                    isLoading = uiState.isSubmitting,
                    enabled = form.isComplete,
                    onClick = onCreate
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .readableWidth()
                .screenContentPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FormIntro(stringResource(Res.string.space_setup_intro))

            SetupSection(stringResource(Res.string.space_setup_section_space)) {
                FormTextField(
                    value = form.name,
                    onValueChange = { onFormChanged(form.copy(name = it)) },
                    label = stringResource(Res.string.space_setup_name),
                    supporting = stringResource(Res.string.space_setup_name_supporting),
                    enabled = enabled,
                    keyboardType = KeyboardType.Text
                )
                FieldLabel(stringResource(Res.string.space_setup_sport))
                SegmentedFilter(
                    options = SportType.entries,
                    selected = form.sport,
                    label = { stringResource(if (it == SportType.FOOTBALL) SharedRes.string.sport_football else SharedRes.string.sport_futsal) },
                    onSelect = { onFormChanged(form.withSport(it)) },
                    shorten = false
                )
            }

            SetupSection(stringResource(SharedRes.string.label_age_group_section)) {
                Text(
                    text = stringResource(Res.string.space_setup_season, seasonName(seasonEndYear)),
                    style = MaterialTheme.typography.bodySmall,
                    color = CarbonGray
                )
                AgeGroupSpanTypeField(
                    label = stringResource(SharedRes.string.label_age_group_span_type),
                    selectedType = form.spanType,
                    onTypeSelected = { onFormChanged(form.withSpanType(it)) }
                )
                if (form.spanType == AgeGroupSpanType.SINGLE_YEAR) {
                    YearOfBirthField(
                        label = stringResource(SharedRes.string.label_year_of_birth),
                        selectedYear = form.endYear,
                        availableYears = yearOptions,
                        onYearSelected = { onFormChanged(form.withEndYear(it)) }
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.weight(1f)) {
                            YearOfBirthField(
                                label = stringResource(SharedRes.string.label_year_of_birth_start),
                                selectedYear = form.startYear,
                                availableYears = yearOptions,
                                onYearSelected = { onFormChanged(form.withStartYear(it)) }
                            )
                        }
                        Box(Modifier.weight(1f)) {
                            YearOfBirthField(
                                label = stringResource(SharedRes.string.label_year_of_birth_end),
                                selectedYear = form.endYear,
                                availableYears = yearOptions,
                                onYearSelected = { onFormChanged(form.withEndYear(it)) }
                            )
                        }
                    }
                    if (form.startYear != null && form.endYear != null && form.startYear < form.endYear) {
                        Text(
                            text = stringResource(SharedRes.string.msg_age_group_range_inverted),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                FormTextField(
                    value = form.ageGroupName,
                    onValueChange = { onFormChanged(form.copy(ageGroupName = it)) },
                    label = stringResource(SharedRes.string.label_age_group_name_field),
                    placeholder = inferredName ?: stringResource(SharedRes.string.label_name_placeholder_pick_year_first),
                    enabled = enabled && form.endYear != null,
                    keyboardType = KeyboardType.Text
                )
                TeamSizeField(
                    label = stringResource(SharedRes.string.label_team_size),
                    selectedTeamSize = form.teamSize,
                    options = form.sport.teamSizes,
                    onTeamSizeSelected = { onFormChanged(form.copy(teamSize = it)) }
                )
            }

            SetupSection(stringResource(Res.string.space_setup_section_team)) {
                FormTextField(
                    value = form.teamName,
                    onValueChange = { onFormChanged(form.copy(teamName = it)) },
                    label = stringResource(Res.string.space_setup_team_name),
                    placeholder = form.defaultTeamName(seasonEndYear),
                    supporting = stringResource(Res.string.space_setup_team_name_supporting),
                    enabled = enabled,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done,
                    onImeAction = onCreate
                )
            }

        }
    }
}

/** One part of the form: a white section with its title. */
@Composable
private fun SetupSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    SectionCard(spacing = 12.dp) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = DeepCarbon.copy(alpha = 0.5f)
        )
        content()
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelSmall, color = DeepCarbon.copy(alpha = 0.5f))
}

private val previewForm = SpaceSetupForm(name = "Fontelas FC", startYear = 2015, endYear = 2015, teamSize = 7)

@Preview
@Composable
private fun SpaceSetupPreview() {
    SquadifyTheme {
        SpaceSetupContent(
            uiState = SpaceSetupUiState(form = previewForm),
            today = LocalDate(2026, 10, 30),
            seasonEndYear = 2027,
            onFormChanged = {},
            onCreate = {},
            onSignOut = {}
        )
    }
}

@Preview(widthDp = 900, heightDp = 480)
@Composable
private fun SpaceSetupLandscapePreview() {
    SquadifyTheme {
        SpaceSetupContent(
            uiState = SpaceSetupUiState(form = previewForm.withSpanType(AgeGroupSpanType.TWO_YEAR).withStartYear(2016)),
            today = LocalDate(2026, 10, 30),
            seasonEndYear = 2027,
            onFormChanged = {},
            onCreate = {},
            onSignOut = {}
        )
    }
}

@Preview
@Composable
private fun SpaceSetupFailedPreview() {
    SquadifyTheme {
        SpaceSetupContent(
            uiState = SpaceSetupUiState(form = previewForm, failed = true),
            today = LocalDate(2026, 10, 30),
            seasonEndYear = 2027,
            onFormChanged = {},
            onCreate = {},
            onSignOut = {}
        )
    }
}
