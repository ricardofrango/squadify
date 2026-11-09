package com.ricardofrango.squadify.coach.presentation.ui.agegroups

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.ricardofrango.squadify.presentation.ui.components.ScreenWindowInsets
import com.ricardofrango.squadify.presentation.ui.components.SectionCard
import com.ricardofrango.squadify.presentation.ui.components.SquadifyTopBar
import com.ricardofrango.squadify.presentation.ui.components.readableWidth
import com.ricardofrango.squadify.presentation.ui.theme.AlertRed
import com.ricardofrango.squadify.presentation.ui.theme.Amber
import com.ricardofrango.squadify.presentation.ui.theme.AmberText
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
import squadify.app.coach.generated.resources.wizard_pick_one
import squadify.app.coach.generated.resources.wizard_plan_room
import squadify.app.coach.generated.resources.wizard_problem_crest_too_big
import squadify.app.coach.generated.resources.wizard_problem_failed
import squadify.app.coach.generated.resources.wizard_problem_plan
import squadify.app.coach.generated.resources.wizard_problem_years
import squadify.app.coach.generated.resources.wizard_season
import squadify.app.coach.generated.resources.wizard_see_plans
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
import squadify.app.coach.generated.resources.wizard_year_taken
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
    onSeePlans: () -> Unit,
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
        onYearToggled = viewModel::onYearToggled,
        onTeamSizeChange = viewModel::onTeamSizeChange,
        onJoinYoungerChange = viewModel::onJoinYoungerChange,
        onTeamsChange = viewModel::onTeamsChange,
        onNext = viewModel::onNext,
        onBack = viewModel::onBack,
        onCreate = viewModel::onCreate,
        onSeePlans = onSeePlans
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
    onYearToggled: (Int) -> Unit,
    onTeamSizeChange: (Int, Int) -> Unit,
    onJoinYoungerChange: (Int, Boolean) -> Unit,
    onTeamsChange: (Int, Int) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onSeePlans: () -> Unit
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
            if (!state.loading && !state.failedToLoad) WizardButtons(state, onBack, onNext, onCreate, onSeePlans)
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
                            1 -> AgeGroupsStep(state, onSportChange, onYearToggled, onTeamSizeChange, onJoinYoungerChange, onSeePlans)
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
        OutlinedTextField(
            value = state.clubName,
            onValueChange = onClubNameChange,
            label = { Text(stringResource(Res.string.wizard_club_name)) },
            placeholder = { Text(stringResource(Res.string.wizard_club_name_placeholder)) },
            singleLine = true,
            isError = nameError != null,
            supportingText = { Text(nameError ?: stringResource(Res.string.wizard_club_hint)) },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PitchGreen, focusedLabelColor = PitchGreen, cursorColor = PitchGreen),
            modifier = Modifier.fillMaxWidth()
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
    onYearToggled: (Int) -> Unit,
    onTeamSizeChange: (Int, Int) -> Unit,
    onJoinYoungerChange: (Int, Boolean) -> Unit,
    onSeePlans: () -> Unit
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
    if (!state.spaceExists) SportChoice(state.sport, onSportChange)
    state.ageGroupsLeft?.let { left ->
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Amber.copy(alpha = 0.15f)).padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(pluralStringResource(Res.plurals.wizard_plan_room, left, left), style = MaterialTheme.typography.bodySmall, color = AmberText, modifier = Modifier.weight(1f))
            TextButton(onClick = onSeePlans) { Text(stringResource(Res.string.wizard_see_plans), color = PitchGreen, fontWeight = FontWeight.Bold) }
        }
    }
    if (state.showErrors && state.picked.isEmpty()) {
        Text(stringResource(Res.string.wizard_pick_one), style = MaterialTheme.typography.bodySmall, color = AlertRed)
    }
    SectionCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        wizardYears(state.seasonEndYear).forEachIndexed { index, year ->
            if (index > 0) HorizontalDivider(color = CarbonGray.copy(alpha = 0.06f))
            YearRow(state, year, onYearToggled, onTeamSizeChange, onJoinYoungerChange)
        }
    }
}

@Composable
private fun SportChoice(sport: SportType, onSportChange: (SportType) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).border(BorderStroke(1.dp, CarbonGray.copy(alpha = 0.2f)), RoundedCornerShape(50))) {
        listOf(SportType.FOOTBALL to Res.string.wizard_football, SportType.FUTSAL to Res.string.wizard_futsal).forEach { (option, label) ->
            val selected = option == sport
            Box(
                Modifier.weight(1f).background(if (selected) PitchGreen.copy(alpha = 0.12f) else Color.White).clickable { onSportChange(option) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text((if (selected) "✓ " else "") + stringResource(label), fontWeight = FontWeight.Bold, color = if (selected) PitchGreen else DeepCarbon)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun YearRow(
    state: AddAgeGroupsUiState,
    year: Int,
    onYearToggled: (Int) -> Unit,
    onTeamSizeChange: (Int, Int) -> Unit,
    onJoinYoungerChange: (Int, Boolean) -> Unit
) {
    val picked = state.picked.firstOrNull { it.oldestYear == year }
    val taken = state.yearsTaken(year)
    val enabled = picked != null || (!taken && state.canPickMore)
    val name = ageGroupNameFor(state.clubName.ifBlank { "?" }, subName(state.seasonEndYear, year))
    val flagged = year in state.takenYears
    Column(
        Modifier.fillMaxWidth()
            .background(if (picked != null) PitchGreen.copy(alpha = 0.05f) else Color.Transparent)
            .then(if (flagged) Modifier.border(BorderStroke(1.dp, AlertRed)) else Modifier)
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled) { onYearToggled(year) }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = picked != null,
                onCheckedChange = { onYearToggled(year) },
                enabled = enabled,
                colors = CheckboxDefaults.colors(checkedColor = PitchGreen)
            )
            val years = if (picked?.joinYounger == true) "$year–${year + 1}" else year.toString()
            Text("$years · ", style = MaterialTheme.typography.bodyMedium, color = CarbonGray.copy(alpha = 0.8f))
            Text(name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (picked != null) FontWeight.Bold else FontWeight.Normal), color = DeepCarbon, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (taken) {
                Text(
                    stringResource(Res.string.wizard_year_taken),
                    style = MaterialTheme.typography.labelSmall,
                    color = CarbonGray,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(CarbonGray.copy(alpha = 0.08f)).padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        if (picked != null) {
            Row(Modifier.padding(start = 48.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.wizard_team_size), style = MaterialTheme.typography.bodySmall, color = CarbonGray.copy(alpha = 0.75f))
                Spacer(Modifier.width(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.sport.teamSizes.forEach { size ->
                        val selected = size == picked.teamSize
                        Box(
                            Modifier.size(32.dp).clip(CircleShape)
                                .background(if (selected) PitchGreen else Color.White)
                                .border(BorderStroke(1.dp, if (selected) PitchGreen else CarbonGray.copy(alpha = 0.25f)), CircleShape)
                                .clickable { onTeamSizeChange(year, size) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(size.toString(), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = if (selected) Color.White else DeepCarbon)
                        }
                    }
                }
            }
            // Futsal joins two consecutive years when there aren't enough players for one.
            if (state.sport == SportType.FUTSAL) {
                val canJoin = picked.joinYounger || !state.yearsTaken(year, startYear = year + 1)
                Row(Modifier.padding(start = 48.dp, bottom = 4.dp).alpha(if (canJoin) 1f else 0.45f), verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = picked.joinYounger,
                        onCheckedChange = { onJoinYoungerChange(year, it) },
                        enabled = canJoin,
                        colors = SwitchDefaults.colors(checkedTrackColor = PitchGreen)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.wizard_join_younger, year + 1), style = MaterialTheme.typography.bodySmall, color = DeepCarbon)
                }
            }
        }
    }
}

@Composable
private fun TeamsStep(state: AddAgeGroupsUiState, onTeamsChange: (Int, Int) -> Unit) {
    Text(stringResource(Res.string.wizard_teams_question), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
    state.picked.forEach { picked ->
        val name = ageGroupNameFor(state.clubName, subName(state.seasonEndYear, picked.oldestYear))
        val years = if (picked.joinYounger) "${picked.oldestYear}–${picked.startYear}" else picked.oldestYear.toString()
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CrestPreview(state, size = 36)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = DeepCarbon, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(Res.string.wizard_teams_born, years, picked.teamSize), style = MaterialTheme.typography.bodySmall, color = CarbonGray.copy(alpha = 0.7f))
                }
                Stepper(picked.teams, onChange = { onTeamsChange(picked.oldestYear, it) })
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "$name · ${teamNames(picked.teams).joinToString(", ")}",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = PitchGreen,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(PitchGreen.copy(alpha = 0.08f)).padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
    SectionCard {
        Text(stringResource(Res.string.wizard_summary), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = CarbonGray.copy(alpha = 0.7f))
        Text(
            "${pluralStringResource(Res.plurals.wizard_summary_age_groups, state.picked.size, state.picked.size)}, " +
                pluralStringResource(Res.plurals.wizard_summary_teams, state.teamCount, state.teamCount),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = DeepCarbon
        )
        Text(stringResource(Res.string.wizard_summary_note), style = MaterialTheme.typography.bodySmall, color = CarbonGray.copy(alpha = 0.75f))
    }
}

@Composable
private fun Stepper(value: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).border(BorderStroke(1.dp, CarbonGray.copy(alpha = 0.2f)), RoundedCornerShape(50)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = { onChange(value - 1) }, enabled = value > 1) { Text("−", fontWeight = FontWeight.Bold, color = if (value > 1) PitchGreen else CarbonGray.copy(alpha = 0.3f)) }
        Text(value.toString(), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
        TextButton(onClick = { onChange(value + 1) }, enabled = value < MAX_WIZARD_TEAMS) { Text("+", fontWeight = FontWeight.Bold, color = if (value < MAX_WIZARD_TEAMS) PitchGreen else CarbonGray.copy(alpha = 0.3f)) }
    }
}

@Composable
private fun WizardButtons(state: AddAgeGroupsUiState, onBack: () -> Unit, onNext: () -> Unit, onCreate: () -> Unit, onSeePlans: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().padding(horizontal = ScreenPadding.Horizontal, vertical = 10.dp)) {
        state.problem?.let { problem ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(
                        when (problem) {
                            WizardProblem.PLAN_LIMIT -> Res.string.wizard_problem_plan
                            WizardProblem.YEARS_TAKEN -> Res.string.wizard_problem_years
                            WizardProblem.CREST_TOO_BIG -> Res.string.wizard_problem_crest_too_big
                            WizardProblem.FAILED -> Res.string.wizard_problem_failed
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = AlertRed,
                    modifier = Modifier.weight(1f)
                )
                if (problem == WizardProblem.PLAN_LIMIT) TextButton(onClick = onSeePlans) { Text(stringResource(Res.string.wizard_see_plans), color = PitchGreen, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(6.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.step > 0) {
                OutlinedButton(
                    onClick = onBack,
                    enabled = !state.submitting,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.3f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DeepCarbon),
                    modifier = Modifier.height(48.dp)
                ) { Text(stringResource(Res.string.wizard_back)) }
            }
            val last = state.step == 2
            Button(
                onClick = if (last) onCreate else onNext,
                enabled = !state.submitting,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PitchGreen),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                if (state.submitting) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else Text(stringResource(if (last) Res.string.wizard_create else Res.string.wizard_next), fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun previewState(step: Int) = AddAgeGroupsUiState(
    loading = false, step = step, spaceExists = true, sport = SportType.FOOTBALL, seasonName = "26/27", seasonEndYear = 2027,
    clubName = "Povoense", ageGroupsLeft = 3,
    picked = listOf(PickedAgeGroup(2017, 7), PickedAgeGroup(2016, 7, teams = 3))
)

@Composable
private fun PreviewWizard(state: AddAgeGroupsUiState) {
    SquadifyTheme {
        AddAgeGroupsContent(state, {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, { _, _ -> }, {}, {}, {}, {})
    }
}

@Preview
@Composable
private fun WizardClubPreview() = PreviewWizard(previewState(0).copy(clubName = "", picked = emptyList()))

@Preview
@Composable
private fun WizardAgeGroupsPreview() = PreviewWizard(previewState(1))

@Preview
@Composable
private fun WizardTeamsPreview() = PreviewWizard(previewState(2))

@Preview(widthDp = 900, heightDp = 480)
@Composable
private fun WizardLandscapePreview() = PreviewWizard(previewState(1))
