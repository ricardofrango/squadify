package com.ricardofrango.squadify.coach.presentation.ui.agegroups

import squadify.app.shared.generated.resources.ic_add
import squadify.app.coach.generated.resources.wizard_add_age_group
import squadify.app.coach.generated.resources.wizard_size_missing
import squadify.app.coach.generated.resources.wizard_year_taken_error
import squadify.app.coach.generated.resources.wizard_year_repeated
import squadify.app.coach.generated.resources.wizard_year_range
import squadify.app.coach.generated.resources.wizard_year_missing
import squadify.app.coach.generated.resources.wizard_birth_year_placeholder
import squadify.app.coach.generated.resources.wizard_birth_year
import squadify.app.coach.generated.resources.wizard_remove
import squadify.app.coach.generated.resources.wizard_age_group_n
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import coil3.compose.AsyncImage
import com.ricardofrango.squadify.coach.presentation.ui.home.SpaceClub
import com.ricardofrango.squadify.coach.presentation.ui.space.ClubCrest
import com.ricardofrango.squadify.coach.presentation.ui.space.ageGroupNameFor
import com.ricardofrango.squadify.model.SportType
import com.ricardofrango.squadify.presentation.ui.components.ErrorScreen
import squadify.app.coach.generated.resources.wizard_creating
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import com.ricardofrango.squadify.presentation.ui.components.AgeGroupsNotInPlanNotice
import com.ricardofrango.squadify.presentation.ui.components.TeamSizeSelector
import com.ricardofrango.squadify.presentation.ui.components.SegmentedFilter
import com.ricardofrango.squadify.presentation.ui.components.CountStepper
import com.ricardofrango.squadify.presentation.ui.components.SecondaryButton
import com.ricardofrango.squadify.presentation.ui.components.PrimaryLoadingButton
import com.ricardofrango.squadify.presentation.ui.components.PinnedBottomBar
import com.ricardofrango.squadify.presentation.ui.components.FormErrorLine
import com.ricardofrango.squadify.presentation.ui.components.FormTextField
import com.ricardofrango.squadify.presentation.ui.components.ScreenWindowInsets
import com.ricardofrango.squadify.presentation.ui.components.SectionCard
import com.ricardofrango.squadify.presentation.ui.components.SquadifyTopBar
import com.ricardofrango.squadify.presentation.ui.components.readableWidth
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.ScreenBackground
import com.ricardofrango.squadify.presentation.ui.theme.ScreenPadding
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import squadify.app.coach.generated.resources.Res
import squadify.app.coach.generated.resources.coach_home_default_space_name
import squadify.app.coach.generated.resources.wizard_back
import squadify.app.coach.generated.resources.wizard_club_hint
import squadify.app.coach.generated.resources.wizard_club_hint_used
import squadify.app.coach.generated.resources.wizard_club_intro
import squadify.app.coach.generated.resources.wizard_club_name
import squadify.app.coach.generated.resources.wizard_club_name_dash
import squadify.app.coach.generated.resources.wizard_club_name_placeholder
import squadify.app.coach.generated.resources.wizard_club_name_required
import squadify.app.coach.generated.resources.wizard_club_question
import squadify.app.coach.generated.resources.wizard_create
import squadify.app.coach.generated.resources.wizard_crest_choose
import squadify.app.coach.generated.resources.wizard_crest_optional
import squadify.app.coach.generated.resources.wizard_futsal
import squadify.app.coach.generated.resources.wizard_football
import squadify.app.coach.generated.resources.wizard_join_younger
import squadify.app.coach.generated.resources.wizard_next
import squadify.app.coach.generated.resources.wizard_pick_by_year
import squadify.app.coach.generated.resources.wizard_problem_crest_too_big
import squadify.app.coach.generated.resources.wizard_problem_failed
import squadify.app.coach.generated.resources.wizard_problem_plan
import squadify.app.coach.generated.resources.wizard_problem_years
import squadify.app.coach.generated.resources.wizard_season
import squadify.app.coach.generated.resources.wizard_step_age_groups
import squadify.app.coach.generated.resources.wizard_step_club
import squadify.app.coach.generated.resources.wizard_step_of
import squadify.app.coach.generated.resources.wizard_step_teams
import squadify.app.coach.generated.resources.wizard_summary
import squadify.app.coach.generated.resources.wizard_summary_age_groups
import squadify.app.coach.generated.resources.wizard_summary_note
import squadify.app.coach.generated.resources.wizard_summary_teams
import squadify.app.coach.generated.resources.wizard_team_size
import squadify.app.coach.generated.resources.wizard_teams_born
import squadify.app.coach.generated.resources.wizard_teams_question
import squadify.app.coach.generated.resources.wizard_title
import squadify.app.coach.generated.resources.wizard_used_clubs
import squadify.app.coach.generated.resources.msg_wizard_failed_to_load
import squadify.app.shared.generated.resources.ic_close
import squadify.app.shared.generated.resources.label_back
import squadify.app.shared.generated.resources.Res as SharedRes

/**
 * The age-group wizard (design "Assistente: Adicionar escalões"), three steps: Clube (the club these age
 * groups play for, its name and crest, or one the space already uses), Escalões (its age groups by birth
 * year in the current season, with their a-side size; futsal can join two years; a new space picks its
 * sport here) and Equipas (how many teams each, "A", "B"…, and a summary). Back between steps keeps what
 * was filled in; everything is created at once on Criar.
 */
@Composable
fun AddAgeGroupsScreen(
    // The coach's own space; null before they have one (the first age groups make it).
    spaceId: String?,
    onClose: () -> Unit,
    onCreated: (AddAgeGroupsEvent.Created) -> Unit,
    viewModel: AddAgeGroupsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val defaultName = stringResource(Res.string.coach_home_default_space_name, "%s")
    LaunchedEffect(spaceId) { viewModel.load(spaceId) { name -> defaultName.replace("%s", name) } }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AddAgeGroupsEvent.Created -> onCreated(event)
            }
        }
    }
    // Back goes to the step before; on the first, it closes.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = state.step > 0 && !state.submitting,
        onBackCompleted = viewModel::onBack
    )
    AddAgeGroupsContent(
        state = state,
        onClose = onClose,
        onRetry = { viewModel.load(spaceId) { name -> defaultName.replace("%s", name) } },
        onClubNameChange = viewModel::onClubNameChange,
        onCrestPicked = viewModel::onCrestPicked,
        onUsedClubPicked = viewModel::onUsedClubPicked,
        onSportChange = viewModel::onSportChange,
        onAddRow = viewModel::onAddRow,
        onRemoveRow = viewModel::onRemoveRow,
        onYearChange = viewModel::onYearChange,
        onTeamSizeChange = viewModel::onTeamSizeChange,
        onJoinYoungerChange = viewModel::onJoinYoungerChange,
        onTeamsChange = viewModel::onTeamsChange,
        onNext = viewModel::onNext,
        onBack = viewModel::onBack,
        onCreate = viewModel::onCreate
    )
}

@Composable
private fun AddAgeGroupsContent(
    state: AddAgeGroupsUiState,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onClubNameChange: (String) -> Unit,
    onCrestPicked: (ByteArray) -> Unit,
    onUsedClubPicked: (SpaceClub) -> Unit,
    onSportChange: (SportType) -> Unit,
    onAddRow: () -> Unit,
    onRemoveRow: (Int) -> Unit,
    onYearChange: (Int, String) -> Unit,
    onTeamSizeChange: (Int, Int) -> Unit,
    onJoinYoungerChange: (Int, Boolean) -> Unit,
    onTeamsChange: (Int, Int) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onCreate: () -> Unit
) {
    Scaffold(
        contentWindowInsets = ScreenWindowInsets,
        containerColor = ScreenBackground,
        topBar = {
            SquadifyTopBar(
                title = stringResource(Res.string.wizard_title),
                showBackButton = false,
                leadingIcon = {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(SharedRes.drawable.ic_close), stringResource(SharedRes.string.label_back), tint = Color.White)
                    }
                }
            )
        },
        bottomBar = {
            if (!state.loading && !state.failedToLoad) WizardButtons(state, onBack, onNext, onCreate)
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding()) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PitchGreen) }
                state.failedToLoad -> ErrorScreen(title = stringResource(Res.string.msg_wizard_failed_to_load), body = null, onRetry = onRetry, modifier = Modifier.fillMaxSize())
                else -> Column(Modifier.fillMaxSize()) {
                    StepIndicator(state.step)
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).readableWidth()
                            .padding(horizontal = ScreenPadding.Horizontal, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        when (state.step) {
                            0 -> ClubStep(state, onClubNameChange, onCrestPicked, onUsedClubPicked)
                            1 -> AgeGroupsStep(state, onSportChange, onAddRow, onRemoveRow, onYearChange, onTeamSizeChange, onJoinYoungerChange)
                            else -> TeamsStep(state, onTeamsChange)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(step: Int) {
    val labels = listOf(Res.string.wizard_step_club, Res.string.wizard_step_age_groups, Res.string.wizard_step_teams)
    Column(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = ScreenPadding.Horizontal, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(labels[step]), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = DeepCarbon, modifier = Modifier.weight(1f))
            Text(stringResource(Res.string.wizard_step_of, step + 1, labels.size), style = MaterialTheme.typography.labelMedium, color = CarbonGray.copy(alpha = 0.7f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            labels.forEachIndexed { index, label ->
                Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (index <= step) PitchGreen else CarbonGray.copy(alpha = 0.12f)))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(label),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (index == step) FontWeight.Bold else FontWeight.Normal),
                        color = if (index == step) DeepCarbon else CarbonGray.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClubStep(
    state: AddAgeGroupsUiState,
    onClubNameChange: (String) -> Unit,
    onCrestPicked: (ByteArray) -> Unit,
    onUsedClubPicked: (SpaceClub) -> Unit
) {
    val scope = rememberCoroutineScope()
    val picker = rememberFilePickerLauncher(type = FileKitType.Image) { file ->
        if (file != null) scope.launch { onCrestPicked(file.readBytes()) }
    }
    Text(stringResource(Res.string.wizard_club_question), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
    Text(stringResource(Res.string.wizard_club_intro), style = MaterialTheme.typography.bodySmall, color = CarbonGray.copy(alpha = 0.75f))
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { picker.launch() }) {
            CrestPreview(state, size = 56)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(stringResource(Res.string.wizard_crest_choose), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = PitchGreen)
                Text(stringResource(Res.string.wizard_crest_optional), style = MaterialTheme.typography.bodySmall, color = CarbonGray.copy(alpha = 0.7f))
            }
        }
        Spacer(Modifier.height(12.dp))
        val nameError = when {
            !state.showErrors -> null
            state.clubName.isBlank() -> stringResource(Res.string.wizard_club_name_required)
            !state.clubNameValid -> stringResource(Res.string.wizard_club_name_dash)
            else -> null
        }
        FormTextField(
            value = state.clubName,
            onValueChange = onClubNameChange,
            label = stringResource(Res.string.wizard_club_name),
            placeholder = stringResource(Res.string.wizard_club_name_placeholder),
            enabled = !state.submitting,
            keyboardType = KeyboardType.Text,
            isError = nameError != null,
            supporting = nameError ?: stringResource(Res.string.wizard_club_hint),
            imeAction = ImeAction.Done
        )
    }
    val used = state.usedClubs
    if (used.isNotEmpty()) {
        Text(stringResource(Res.string.wizard_used_clubs), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            used.forEach { club ->
                val selected = club.name.equals(state.clubName.trim(), ignoreCase = true)
                Row(
                    Modifier.clip(RoundedCornerShape(50))
                        .background(if (selected) PitchGreen.copy(alpha = 0.1f) else Color.White)
                        .border(BorderStroke(1.dp, if (selected) PitchGreen else CarbonGray.copy(alpha = 0.2f)), RoundedCornerShape(50))
                        .clickable { onUsedClubPicked(club) }
                        .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ClubCrest(club.name, club.logoUrl, size = 24.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(club.name, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
                }
            }
        }
        Text(stringResource(Res.string.wizard_club_hint_used), style = MaterialTheme.typography.bodySmall, color = CarbonGray.copy(alpha = 0.7f))
    }
}

/** The crest as it is now: the picked image, a crest the space already uses, the club's initials, or "?". */
@Composable
private fun CrestPreview(state: AddAgeGroupsUiState, size: Int) {
    when {
        state.crest != null -> AsyncImage(
            model = state.crest,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.22f).dp)).background(Color.White)
        )
        state.crestPath != null || state.clubName.isNotBlank() -> ClubCrest(state.clubName.ifBlank { "?" }, state.crestPath, size = size.dp)
        else -> Box(
            Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.22f).dp)).background(CarbonGray.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Text("?", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = CarbonGray.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun AgeGroupsStep(
    state: AddAgeGroupsUiState,
    onSportChange: (SportType) -> Unit,
    onAddRow: () -> Unit,
    onRemoveRow: (Int) -> Unit,
    onYearChange: (Int, String) -> Unit,
    onTeamSizeChange: (Int, Int) -> Unit,
    onJoinYoungerChange: (Int, Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CrestPreview(state, size = 36)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(state.clubName.trim(), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = DeepCarbon, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(Res.string.wizard_pick_by_year), style = MaterialTheme.typography.bodySmall, color = CarbonGray.copy(alpha = 0.7f))
        }
        state.seasonName?.let {
            Text(
                stringResource(Res.string.wizard_season, it),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = DeepCarbon,
                modifier = Modifier.border(BorderStroke(1.dp, CarbonGray.copy(alpha = 0.2f)), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }
    }
    // A new space picks its sport here; an existing one keeps its own.
    if (!state.spaceExists) {
        SegmentedFilter(
            options = listOf(SportType.FOOTBALL, SportType.FUTSAL),
            selected = state.sport,
            label = { stringResource(if (it == SportType.FOOTBALL) Res.string.wizard_football else Res.string.wizard_futsal) },
            onSelect = onSportChange,
            shorten = false
        )
    }
    state.rows.forEachIndexed { index, row ->
        AgeGroupBox(
            state = state,
            row = row,
            number = index + 1,
            onRemove = { onRemoveRow(row.key) }.takeIf { state.rows.size > 1 },
            onYearChange = { onYearChange(row.key, it) },
            onTeamSizeChange = { onTeamSizeChange(row.key, it) },
            onJoinYoungerChange = { onJoinYoungerChange(row.key, it) }
        )
    }
    // Another age group while the plan has room; when it hasn't, the same box as everywhere a plan stops.
    val plan = state.plan
    if (state.canAddRow) {
        SecondaryButton(stringResource(Res.string.wizard_add_age_group), onClick = onAddRow, icon = SharedRes.drawable.ic_add)
    } else if (plan != null) {
        AgeGroupsNotInPlanNotice(plan)
    }
}

/**
 * One age group to create: its birth year typed in, then its name ("2016 · Povoense - Sub 11"), how many
 * players on the pitch (none until picked) and in futsal joining the next younger year. What's wrong shows
 * under it once Seguinte was tried. Removable when there's more than one.
 */
@Composable
private fun AgeGroupBox(
    state: AddAgeGroupsUiState,
    row: AgeGroupRow,
    number: Int,
    onRemove: (() -> Unit)?,
    onYearChange: (String) -> Unit,
    onTeamSizeChange: (Int) -> Unit,
    onJoinYoungerChange: (Boolean) -> Unit
) {
    val problem = state.problemOf(row).takeIf { state.showErrors }
    val oldest = row.oldestYear
    SectionCard(spacing = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.wizard_age_group_n, number), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = CarbonGray.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
            if (onRemove != null) {
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(painterResource(SharedRes.drawable.ic_close), stringResource(Res.string.wizard_remove), tint = CarbonGray.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                }
            }
        }
        val yearProblem = problem?.takeIf { it != RowProblem.SIZE_MISSING }
        FormTextField(
            value = row.yearText,
            onValueChange = onYearChange,
            label = stringResource(Res.string.wizard_birth_year),
            placeholder = stringResource(Res.string.wizard_birth_year_placeholder),
            enabled = !state.submitting,
            keyboardType = KeyboardType.Number,
            isError = yearProblem != null,
            supporting = when (yearProblem) {
                RowProblem.YEAR_MISSING -> stringResource(Res.string.wizard_year_missing)
                RowProblem.YEAR_OUT_OF_RANGE -> wizardYearRange(state.seasonEndYear).let { stringResource(Res.string.wizard_year_range, it.first, it.last) }
                RowProblem.YEAR_REPEATED -> stringResource(Res.string.wizard_year_repeated)
                RowProblem.YEAR_TAKEN -> stringResource(Res.string.wizard_year_taken_error)
                else -> oldest?.takeIf { it in wizardYearRange(state.seasonEndYear) }?.let {
                    val years = if (row.joinYounger) "$it–${it + 1}" else it.toString()
                    "$years · " + ageGroupNameFor(state.clubName, subName(state.seasonEndYear, it))
                }
            },
            imeAction = ImeAction.Done
        )
        Text(stringResource(Res.string.wizard_team_size), style = MaterialTheme.typography.bodySmall, color = CarbonGray.copy(alpha = 0.75f))
        TeamSizeSelector(options = state.sport.teamSizes, selected = row.teamSize, onSelected = onTeamSizeChange)
        if (problem == RowProblem.SIZE_MISSING) FormErrorLine(stringResource(Res.string.wizard_size_missing))
        // Futsal joins two consecutive years when there aren't enough players for one.
        if (state.sport == SportType.FUTSAL && oldest != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = row.joinYounger,
                    onCheckedChange = onJoinYoungerChange,
                    colors = SwitchDefaults.colors(checkedTrackColor = PitchGreen)
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.wizard_join_younger, oldest + 1), style = MaterialTheme.typography.bodySmall, color = DeepCarbon)
            }
        }
    }
}

@Composable
private fun TeamsStep(state: AddAgeGroupsUiState, onTeamsChange: (Int, Int) -> Unit) {
    Text(stringResource(Res.string.wizard_teams_question), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
    state.rows.forEach { row ->
        val oldest = row.oldestYear ?: return@forEach
        val name = ageGroupNameFor(state.clubName, subName(state.seasonEndYear, oldest))
        val years = if (row.joinYounger) "$oldest–${oldest + 1}" else oldest.toString()
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CrestPreview(state, size = 36)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = DeepCarbon, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(Res.string.wizard_teams_born, years, row.teamSize ?: 0), style = MaterialTheme.typography.bodySmall, color = CarbonGray.copy(alpha = 0.7f))
                }
                CountStepper(row.teams, onChange = { onTeamsChange(row.key, it) }, min = 1, max = MAX_WIZARD_TEAMS)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "$name · ${teamNames(row.teams).joinToString(", ")}",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = PitchGreen,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(PitchGreen.copy(alpha = 0.08f)).padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
    SectionCard {
        Text(stringResource(Res.string.wizard_summary), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = CarbonGray.copy(alpha = 0.7f))
        Text(
            "${pluralStringResource(Res.plurals.wizard_summary_age_groups, state.rows.size, state.rows.size)}, " +
                pluralStringResource(Res.plurals.wizard_summary_teams, state.teamCount, state.teamCount),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = DeepCarbon
        )
        Text(stringResource(Res.string.wizard_summary_note), style = MaterialTheme.typography.bodySmall, color = CarbonGray.copy(alpha = 0.75f))
    }
}

@Composable
private fun WizardButtons(state: AddAgeGroupsUiState, onBack: () -> Unit, onNext: () -> Unit, onCreate: () -> Unit) {
    PinnedBottomBar {
        state.problem?.let { problem ->
            val plan = state.plan
            if (problem == WizardProblem.PLAN_LIMIT && plan != null) {
                AgeGroupsNotInPlanNotice(plan)
            } else {
                FormErrorLine(
                    stringResource(
                        when (problem) {
                            WizardProblem.PLAN_LIMIT -> Res.string.wizard_problem_plan
                            WizardProblem.YEARS_TAKEN -> Res.string.wizard_problem_years
                            WizardProblem.CREST_TOO_BIG -> Res.string.wizard_problem_crest_too_big
                            WizardProblem.FAILED -> Res.string.wizard_problem_failed
                        }
                    )
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.step > 0) {
                SecondaryButton(stringResource(Res.string.wizard_back), onClick = onBack, enabled = !state.submitting, neutral = true, modifier = Modifier.weight(1f))
            }
            val last = state.step == 2
            PrimaryLoadingButton(
                text = if (last) Res.string.wizard_create else Res.string.wizard_next,
                submittingText = Res.string.wizard_creating,
                isLoading = state.submitting,
                onClick = if (last) onCreate else onNext,
                modifier = Modifier.weight(if (state.step > 0) 1.6f else 1f)
            )
        }
    }
}

private fun previewState(step: Int) = AddAgeGroupsUiState(
    loading = false, step = step, spaceExists = true, sport = SportType.FOOTBALL, seasonName = "26/27", seasonEndYear = 2027,
    clubName = "Povoense", ageGroupsLeft = 3,
    rows = listOf(AgeGroupRow(0, "2017", teamSize = 7), AgeGroupRow(1, "2016", teams = 3))
)

@Composable
private fun PreviewWizard(state: AddAgeGroupsUiState) {
    SquadifyTheme {
        AddAgeGroupsContent(state, {}, {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, { _, _ -> }, { _, _ -> }, {}, {}, {})
    }
}

@Preview
@Composable
private fun WizardClubPreview() = PreviewWizard(previewState(0).copy(clubName = "", rows = listOf(AgeGroupRow(0))))

@Preview
@Composable
private fun WizardAgeGroupsPreview() = PreviewWizard(previewState(1))

@Preview
@Composable
private fun WizardTeamsPreview() = PreviewWizard(previewState(2))

@Preview(widthDp = 900, heightDp = 480)
@Composable
private fun WizardLandscapePreview() = PreviewWizard(previewState(1))
