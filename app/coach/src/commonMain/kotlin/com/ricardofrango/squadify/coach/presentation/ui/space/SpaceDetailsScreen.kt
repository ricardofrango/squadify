package com.ricardofrango.squadify.coach.presentation.ui.space

import com.ricardofrango.squadify.domain.config.FeatureFlagsStore
import org.koin.compose.koinInject
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import squadify.app.coach.generated.resources.wizard_summary_teams
import squadify.app.coach.generated.resources.wizard_summary_age_groups
import squadify.app.coach.generated.resources.space_details_ok
import squadify.app.coach.generated.resources.space_details_import_failed
import squadify.app.coach.generated.resources.space_details_import_training_skipped
import squadify.app.coach.generated.resources.space_details_import_age_groups
import squadify.app.coach.generated.resources.space_details_new
import squadify.app.coach.generated.resources.space_details_created_no_crest
import squadify.app.coach.generated.resources.space_details_created
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.model.ClubStatus
import com.ricardofrango.squadify.model.SpaceAgeGroup
import com.ricardofrango.squadify.model.SpaceDetails
import com.ricardofrango.squadify.model.SpaceNextMatch
import com.ricardofrango.squadify.model.SpacePlan
import com.ricardofrango.squadify.model.SportType
import com.ricardofrango.squadify.presentation.ui.components.AdBanner
import com.ricardofrango.squadify.presentation.ui.components.ErrorScreen
import com.ricardofrango.squadify.presentation.ui.components.PrimaryFab
import com.ricardofrango.squadify.presentation.ui.components.AgeGroupsNotInPlanDialog
import com.ricardofrango.squadify.presentation.ui.components.AgeGroupsNotInPlanNotice
import com.ricardofrango.squadify.data.remote.isNotInPlan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.ricardofrango.squadify.presentation.ui.components.ScreenWindowInsets
import com.ricardofrango.squadify.presentation.ui.components.SectionCard
import com.ricardofrango.squadify.presentation.ui.components.SquadifyMark
import com.ricardofrango.squadify.presentation.ui.components.displayName
import com.ricardofrango.squadify.presentation.ui.components.readableWidth
import com.ricardofrango.squadify.presentation.ui.components.weekdayDateLabel
import com.ricardofrango.squadify.presentation.ui.opponents.OpponentsBrowser
import com.ricardofrango.squadify.presentation.ui.theme.AlertRed
import com.ricardofrango.squadify.presentation.ui.theme.Amber
import com.ricardofrango.squadify.presentation.ui.theme.AmberText
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.ClubChrome
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.agegroupimport.AgeGroupHandoff
import com.ricardofrango.squadify.presentation.agegroupimport.AgeGroupImportDialog
import com.ricardofrango.squadify.presentation.agegroupimport.CLUBS_APP_PACKAGES
import com.ricardofrango.squadify.presentation.agegroupimport.rememberAgeGroupHandoff
import com.ricardofrango.squadify.presentation.ui.theme.ScreenBackground
import com.ricardofrango.squadify.presentation.ui.theme.ScreenPadding
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import squadify.app.coach.generated.resources.Res
import squadify.app.coach.generated.resources.coach_home_locked
import squadify.app.coach.generated.resources.msg_space_details_failed
import squadify.app.coach.generated.resources.space_details_add_age_groups
import squadify.app.coach.generated.resources.space_details_born
import squadify.app.coach.generated.resources.space_details_count
import squadify.app.coach.generated.resources.space_details_edit
import squadify.app.coach.generated.resources.space_details_empty_body
import squadify.app.coach.generated.resources.space_details_empty_title
import squadify.app.coach.generated.resources.space_details_new_opponent
import squadify.app.coach.generated.resources.space_details_next_game
import squadify.app.coach.generated.resources.space_details_no_teams
import squadify.app.coach.generated.resources.space_details_owned_by
import squadify.app.coach.generated.resources.space_details_staff
import squadify.app.coach.generated.resources.space_details_staff_note
import squadify.app.coach.generated.resources.space_details_tab_age_groups
import squadify.app.coach.generated.resources.space_details_tab_opponents
import squadify.app.coach.generated.resources.space_details_teams
import squadify.app.shared.generated.resources.ic_add
import squadify.app.shared.generated.resources.ic_back
import squadify.app.shared.generated.resources.ic_calendar
import squadify.app.shared.generated.resources.ic_edit
import squadify.app.shared.generated.resources.label_back
import kotlin.time.Instant
import squadify.app.shared.generated.resources.Res as SharedRes

/**
 * One space's details (the coach app; design "Detalhes do espaço"): the space's crest, name, season and plan,
 * and two tabs. "Escalões": its age groups (all of them for the owner, their own for staff), by club and
 * age, each with its crest, birth years, teams and next game; the owner adds more. "Adversários": the
 * space's one opponents list, for every age group. The owner edits the space (its name and image) from the
 * pencil. Staff see whose space it is and only their age groups.
 */
@Composable
fun SpaceDetailsScreen(
    spaceId: String,
    onNavigateBack: () -> Unit,
    onOpenAgeGroup: (ageGroupId: String) -> Unit,
    onAddAgeGroups: (space: Club) -> Unit,
    onEditSpace: () -> Unit,
    onOpenOpponent: (opponentId: String) -> Unit,
    onCreateOpponent: (name: String) -> Unit,
    // Set when a screen opened from here changed something: reload.
    refresh: Boolean = false,
    onRefreshed: () -> Unit = {},
    // The age groups the wizard just created, marked new, with a message saying so once.
    newAgeGroupIds: List<String> = emptyList(),
    newTeams: Int = 0,
    crestSaved: Boolean = true,
    viewModel: SpaceDetailsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    LaunchedEffect(spaceId) { viewModel.load(spaceId) }
    // Copying an age group from the clubs app (TECHNICAL_DECISIONS §81): shown to the owner when that app is installed.
    var importing by remember { mutableStateOf<AgeGroupHandoff?>(null) }
    val handoff = rememberAgeGroupHandoff(CLUBS_APP_PACKAGES) { importing = it }
    // The import is shown only when the server's feature flag is on, and the clubs app is installed.
    val featureFlags by koinInject<FeatureFlagsStore>().flags.collectAsStateWithLifecycle()
    // Adding or importing an age group first asks the coach server for room (its plan decides, not the app). A refusal
    // opens the popup; anything else goes on, and the server refuses again if the room has gone by the time it's made.
    val scope = rememberCoroutineScope()
    var planRefused by remember { mutableStateOf(false) }
    // The imports that left out their training (the space's plan has none): counted, so each shows once.
    var importedWithoutTraining by remember { mutableIntStateOf(0) }
    // The imports that failed: the dialog closes on a failure (its grant is used up), so the failure is said in a snackbar.
    var importFailures by remember { mutableIntStateOf(0) }
    fun askRoom(then: () -> Unit) {
        scope.launch {
            try {
                viewModel.checkAgeGroupRoom(spaceId)
                then()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e.isNotInPlan) planRefused = true else then()
            }
        }
    }
    LaunchedEffect(refresh) {
        if (refresh) {
            onRefreshed()
            viewModel.load(spaceId, force = true)
        }
    }
    SpaceDetailsContent(
        state = state,
        spaceId = spaceId,
        onNavigateBack = onNavigateBack,
        onRetry = { viewModel.load(spaceId, force = true) },
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refreshContent,
        onOpenAgeGroup = onOpenAgeGroup,
        onAddAgeGroups = { space -> askRoom { onAddAgeGroups(space) } },
        onEditSpace = onEditSpace,
        opponents = { OpponentsBrowser(spaceId, onOpponentClick = { onOpenOpponent(it.id) }, onCreateOpponent = onCreateOpponent, refresh = refresh) },
        onCreateOpponent = { onCreateOpponent("") },
        onImport = if (handoff.installed && featureFlags.ageGroupImport) ({ askRoom(handoff::start) }) else null,
        importedWithoutTraining = importedWithoutTraining,
        trainingSkippedMessage = stringResource(Res.string.space_details_import_training_skipped),
        importFailures = importFailures,
        importFailedMessage = stringResource(Res.string.space_details_import_failed),
        newAgeGroupIds = newAgeGroupIds,
        createdMessage = if (newAgeGroupIds.isEmpty()) null else stringResource(
            if (crestSaved) Res.string.space_details_created else Res.string.space_details_created_no_crest,
            pluralStringResource(Res.plurals.wizard_summary_age_groups, newAgeGroupIds.size, newAgeGroupIds.size),
            pluralStringResource(Res.plurals.wizard_summary_teams, newTeams, newTeams)
        )
    )
    importing?.let { handoff ->
        val details = (state as? SpaceDetailsUiState.Loaded)?.details
        AgeGroupImportDialog(
            clubId = spaceId,
            clubSport = details?.space?.sport ?: SportType.FOOTBALL,
            ownPlan = details?.plan,
            handoff = handoff,
            onImported = { result ->
                importing = null
                if (result.trainingSkipped) importedWithoutTraining++
                viewModel.load(spaceId, force = true)
            },
            onFailed = {
                importing = null
                importFailures++
            },
            onDismiss = { importing = null }
        )
    }
    if (planRefused) {
        (state as? SpaceDetailsUiState.Loaded)?.details?.plan?.let { plan ->
            AgeGroupsNotInPlanDialog(plan, onDismiss = { planRefused = false })
        }
    }
}

@Composable
private fun SpaceDetailsContent(
    state: SpaceDetailsUiState,
    spaceId: String,
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenAgeGroup: (String) -> Unit,
    onAddAgeGroups: (Club) -> Unit,
    onEditSpace: () -> Unit,
    opponents: @Composable () -> Unit,
    onCreateOpponent: () -> Unit,
    newAgeGroupIds: List<String> = emptyList(),
    createdMessage: String? = null,
    onImport: (() -> Unit)? = null,
    importedWithoutTraining: Int = 0,
    trainingSkippedMessage: String = "",
    importFailures: Int = 0,
    importFailedMessage: String = "",
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {}
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    // Once, not again after a rotation.
    var announced by rememberSaveable { mutableStateOf(false) }
    val ok = stringResource(Res.string.space_details_ok)
    LaunchedEffect(createdMessage) {
        if (createdMessage != null && !announced) {
            announced = true
            snackbar.showSnackbar(createdMessage, actionLabel = ok, duration = SnackbarDuration.Long)
        }
    }
    LaunchedEffect(importedWithoutTraining) {
        if (importedWithoutTraining > 0) snackbar.showSnackbar(trainingSkippedMessage, duration = SnackbarDuration.Long)
    }
    LaunchedEffect(importFailures) {
        if (importFailures > 0) snackbar.showSnackbar(importFailedMessage, duration = SnackbarDuration.Long)
    }
    val loaded = state as? SpaceDetailsUiState.Loaded
    val isOwner = loaded?.details?.isOwner == true
    Scaffold(
        contentWindowInsets = ScreenWindowInsets,
        containerColor = ScreenBackground,
        topBar = { SpaceHeader(loaded?.details, tab, onSelectTab = { tab = it }, onNavigateBack = onNavigateBack, onEditSpace = onEditSpace) },
        bottomBar = { AdBanner(spaceId = spaceId, alone = true) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            // The owner adds age groups, and opponents; staff open what's there.
            if (loaded != null && isOwner) {
                val (label, action) = if (tab == 0) {
                    stringResource(Res.string.space_details_add_age_groups) to { onAddAgeGroups(loaded.details.space) }
                } else {
                    stringResource(Res.string.space_details_new_opponent) to onCreateOpponent
                }
                PrimaryFab(label, onClick = action)
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                SpaceDetailsUiState.Loading -> Unit
                SpaceDetailsUiState.Failed -> ErrorScreen(title = stringResource(Res.string.msg_space_details_failed), body = null, onRetry = onRetry, modifier = Modifier.fillMaxSize())
                is SpaceDetailsUiState.Loaded -> if (tab == 0) {
                    // Pull down to read the space again (the server, not the cache: TECHNICAL_DECISIONS §83).
                    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                        AgeGroupList(state, newAgeGroupIds, onOpenAgeGroup, onImport)
                    }
                } else {
                    opponents()
                }
            }
        }
    }
}

@Composable
private fun SpaceHeader(
    details: SpaceDetails?,
    tab: Int,
    onSelectTab: (Int) -> Unit,
    onNavigateBack: () -> Unit,
    onEditSpace: (() -> Unit)?
) {
    Column(
        Modifier.fillMaxWidth().background(ClubChrome)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onNavigateBack) {
                Icon(painterResource(SharedRes.drawable.ic_back), stringResource(SharedRes.string.label_back), tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            if (onEditSpace != null) {
                IconButton(onClick = onEditSpace) {
                    Icon(painterResource(SharedRes.drawable.ic_edit), stringResource(Res.string.space_details_edit), tint = Color.White)
                }
            }
        }
        if (details != null) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SpaceCrest(details.space, size = 52.dp)
                Spacer(Modifier.width(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        details.space.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        details.seasonName?.let { SeasonChip(it) }
                        if (details.isOwner) {
                            Badge(details.plan.displayName(), PitchGreen)
                        } else {
                            Badge(stringResource(Res.string.space_details_staff), Color.White.copy(alpha = 0.2f))
                            details.ownerName?.let {
                                Text(stringResource(Res.string.space_details_owned_by, it), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
        PrimaryTabRow(
            selectedTabIndex = tab,
            containerColor = ClubChrome,
            contentColor = Color.White,
            indicator = { TabRowDefaults.PrimaryIndicator(Modifier.tabIndicatorOffset(tab), width = 64.dp, color = PitchGreen) },
            divider = {}
        ) {
            listOf(Res.string.space_details_tab_age_groups, Res.string.space_details_tab_opponents).forEachIndexed { index, label ->
                Tab(
                    selected = tab == index,
                    onClick = { onSelectTab(index) },
                    selectedContentColor = Color.White,
                    unselectedContentColor = Color.White.copy(alpha = 0.6f),
                    text = { Text(stringResource(label), fontWeight = if (tab == index) FontWeight.Bold else FontWeight.Normal) }
                )
            }
        }
    }
}

/** The space's image, or the Squadify mark on green until it has one. */
@Composable
fun SpaceCrest(space: Club, size: androidx.compose.ui.unit.Dp) {
    if (space.logoUrl != null) {
        SpaceLogo(space, size)
    } else {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(size * 0.22f)).background(PitchGreen),
            contentAlignment = Alignment.Center
        ) {
            SquadifyMark(size = size * 0.8f, lightDotColor = Color.White, darkDotColor = Color.White.copy(alpha = 0.85f), onTile = false)
        }
    }
}

@Composable
private fun SeasonChip(name: String) {
    Row(
        Modifier.border(BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(painterResource(SharedRes.drawable.ic_calendar), contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
        Text(name, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
    }
}

@Composable
private fun Badge(text: String, background: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = Color.White,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(background).padding(horizontal = 10.dp, vertical = 2.dp)
    )
}

@Composable
private fun AgeGroupList(
    state: SpaceDetailsUiState.Loaded,
    newAgeGroupIds: List<String>,
    onOpenAgeGroup: (String) -> Unit,
    onImport: (() -> Unit)? = null
) {
    val details = state.details
    if (state.ageGroups.isEmpty()) {
        // The box, and the import under it: the floating button adds age groups.
        Column(Modifier.fillMaxSize()) {
            EmptyAgeGroups()
            if (details.isOwner && onImport != null) ImportAgeGroupsButton(onImport)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().readableWidth(),
        // Room at the bottom for the floating button.
        contentPadding = PaddingValues(start = ScreenPadding.Horizontal, end = ScreenPadding.Horizontal, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!details.isOwner) {
            item(key = "staff-note") { Note(stringResource(Res.string.space_details_staff_note)) }
        }
        // The owner's plan has no room for more age groups this season.
        if (details.isOwner && !details.canAddAgeGroups) {
            item(key = "plan-full") { AgeGroupsNotInPlanNotice(details.plan) }
        }
        if (details.isOwner && onImport != null) item(key = "import") { ImportAgeGroupsButton(onImport) }
        item(key = "count") {
            Text(
                pluralStringResource(Res.plurals.space_details_count, state.ageGroups.size, state.ageGroups.size),
                style = MaterialTheme.typography.bodySmall,
                color = CarbonGray.copy(alpha = 0.7f)
            )
        }
        items(state.ageGroups, key = { it.ageGroup.id }) { row ->
            AgeGroupCard(row, details.plan, isNew = row.ageGroup.id in newAgeGroupIds, onClick = { onOpenAgeGroup(row.ageGroup.id) }.takeUnless { row.locked })
        }
    }
}

@Composable
private fun AgeGroupCard(row: SpaceAgeGroup, plan: SpacePlan, isNew: Boolean, onClick: (() -> Unit)?) {
    val modifier = when {
        row.locked -> Modifier.alpha(0.6f)
        isNew -> Modifier.border(BorderStroke(1.5.dp, PitchGreen), RoundedCornerShape(16.dp))
        else -> Modifier
    }
    SectionCard(onClick = onClick, contentPadding = PaddingValues(0.dp), modifier = modifier) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            AgeGroupCrest(row.ageGroup, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(row.ageGroup.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (isNew) {
                        Spacer(Modifier.width(6.dp))
                        Badge(stringResource(Res.string.space_details_new), PitchGreen)
                    }
                }
                Text(stringResource(Res.string.space_details_born, bornYears(row.ageGroup)), style = MaterialTheme.typography.bodySmall, color = CarbonGray.copy(alpha = 0.7f))
                Text(teamsLine(row.teams), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
                if (row.locked) {
                    Text(stringResource(Res.string.coach_home_locked, plan.displayName()), style = MaterialTheme.typography.bodySmall, color = AlertRed)
                }
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = CarbonGray.copy(alpha = 0.4f))
        }
        row.nextMatch?.let { match ->
            HorizontalDivider(color = CarbonGray.copy(alpha = 0.08f))
            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(SharedRes.drawable.ic_calendar), contentDescription = null, tint = PitchGreen, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(Res.string.space_details_next_game, nextGameDate(match), match.opponentName),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = PitchGreen,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** "Equipa A", "Equipas A, B", or none yet. */
@Composable
private fun teamsLine(teams: List<String>): String = when (teams.size) {
    0 -> stringResource(Res.string.space_details_no_teams)
    else -> pluralStringResource(Res.plurals.space_details_teams, teams.size, teams.joinToString(", "))
}

@Composable
private fun nextGameDate(match: SpaceNextMatch): String =
    weekdayDateLabel(match.dateTime.toLocalDateTime(TimeZone.currentSystemDefault()).date)

/** "2015", or "2015–2016" for a two-year age group. */
private fun bornYears(ageGroup: AgeGroup): String =
    if (ageGroup.startYear == ageGroup.endYear) ageGroup.endYear.toString()
    else "${minOf(ageGroup.startYear, ageGroup.endYear)}–${maxOf(ageGroup.startYear, ageGroup.endYear)}"

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = CarbonGray.copy(alpha = 0.75f))
}

@Composable
private fun ImportAgeGroupsButton(onImport: () -> Unit) {
    TextButton(onClick = onImport, modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding.Horizontal)) {
        Text(stringResource(Res.string.space_details_import_age_groups), color = PitchGreen, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EmptyAgeGroups() {
    Box(Modifier.fillMaxWidth().padding(ScreenPadding.Horizontal), contentAlignment = Alignment.TopCenter) {
        SectionCard(modifier = Modifier.widthIn(max = 480.dp).padding(top = 12.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(PitchGreen.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                    SquadifyMark(size = 44.dp, onTile = false)
                }
                Text(stringResource(Res.string.space_details_empty_title), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
                Text(stringResource(Res.string.space_details_empty_body), style = MaterialTheme.typography.bodySmall, color = CarbonGray.copy(alpha = 0.75f))
            }
        }
    }
}

private val previewSpace = Club(id = "space-1", name = "Espaço de Ana Silva", status = ClubStatus.ACTIVE, createdAt = Instant.fromEpochMilliseconds(0), sport = SportType.FOOTBALL)

private fun previewAgeGroup(id: String, name: String, year: Int, teams: List<String>, next: Boolean = false) = SpaceAgeGroup(
    AgeGroup(id = id, clubId = "space-1", name = name, seasonId = "s1", startYear = year, endYear = year, teamSize = 7),
    teams,
    nextMatch = if (next) SpaceNextMatch("m1", Instant.parse("2026-11-14T10:00:00Z"), "Alverca") else null
)

private val previewDetails = SpaceDetails(
    space = previewSpace, isOwner = true, plan = SpacePlan("PRO"), seasonName = "26/27",
    ageGroups = listOf(
        previewAgeGroup("1", "CF Santa Iria - Sub 9", 2018, listOf("A"), next = true),
        previewAgeGroup("2", "Povoense - Sub 12", 2015, listOf("A", "B"), next = true),
        previewAgeGroup("3", "Povoense - Sub 13", 2014, listOf("A"))
    )
)

@Preview
@Composable
private fun SpaceDetailsPreview() {
    SquadifyTheme {
        SpaceDetailsContent(SpaceDetailsUiState.Loaded(previewDetails), "space-1", {}, {}, {}, {}, {}, opponents = {}, onCreateOpponent = {})
    }
}

@Preview
@Composable
private fun SpaceDetailsStaffPreview() {
    SquadifyTheme {
        SpaceDetailsContent(
            SpaceDetailsUiState.Loaded(previewDetails.copy(isOwner = false, ownerName = "João Pereira", space = previewSpace.copy(name = "Espaço de João Pereira"))),
            "space-1", {}, {}, {}, {}, {}, opponents = {}, onCreateOpponent = {}
        )
    }
}

@Preview
@Composable
private fun SpaceDetailsEmptyPreview() {
    SquadifyTheme {
        SpaceDetailsContent(SpaceDetailsUiState.Loaded(previewDetails.copy(ageGroups = emptyList(), plan = SpacePlan("FREE"))), "space-1", {}, {}, {}, {}, {}, opponents = {}, onCreateOpponent = {})
    }
}

@Preview(widthDp = 900, heightDp = 480)
@Composable
private fun SpaceDetailsLandscapePreview() {
    SquadifyTheme {
        SpaceDetailsContent(SpaceDetailsUiState.Loaded(previewDetails), "space-1", {}, {}, {}, {}, {}, opponents = {}, onCreateOpponent = {})
    }
}
