package com.ricardofrango.squadify.presentation.ui.agegroupdetails

import com.ricardofrango.squadify.presentation.ui.components.weekdayDateLabel
import com.ricardofrango.squadify.presentation.ui.components.safeSidesPadding
import com.ricardofrango.squadify.presentation.ui.components.ScreenWindowInsets
import com.ricardofrango.squadify.presentation.ui.components.besideRail
import com.ricardofrango.squadify.presentation.ui.components.ErrorScreen
import com.ricardofrango.squadify.presentation.ui.components.ShownSpace
import squadify.app.shared.generated.resources.msg_refresh_failed
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.ricardofrango.squadify.presentation.ui.components.OnReturnToApp
import com.ricardofrango.squadify.presentation.ui.components.useNavigationRail
import com.ricardofrango.squadify.presentation.ui.components.isLandscapeLayout
import com.ricardofrango.squadify.presentation.ui.theme.ClubChrome
import com.ricardofrango.squadify.presentation.ui.theme.ChromeHighlight
import com.ricardofrango.squadify.presentation.ui.theme.ScreenPadding
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyClubTheme
import squadify.app.shared.generated.resources.msg_player_removed_age_group
import org.jetbrains.compose.resources.getString
import com.ricardofrango.squadify.presentation.navigation.collectSettledState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricardofrango.squadify.model.*
import com.ricardofrango.squadify.presentation.ui.components.DetailBottomBar
import com.ricardofrango.squadify.presentation.ui.components.DetailRail
import com.ricardofrango.squadify.presentation.ui.components.DetailTab
import com.ricardofrango.squadify.presentation.ui.components.SquadifyTopBar
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import com.ricardofrango.squadify.presentation.ui.theme.ScreenBackground
import com.ricardofrango.squadify.presentation.ui.theme.screenContentPadding
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import squadify.app.shared.generated.resources.*
import kotlin.time.Instant



/**
 * The hub for one age group (design 33): Stats · Teams · Matches · Members tabs under a fixed header
 * (name, "Born 2012 · 26/27", and Teams · Players · Staff · Upcoming -- each number switches to its
 * tab). Always opens on Stats. From a medium-width window up the bottom bar becomes a side rail (33e). Each add/manage
 * affordance is gated on its own `AgeGroupDetails` flag -- `canAddStaff` (coordinator+ only),
 * `canCreateTeam`, `canManageMatch` (schedule), or `canAssignPlayersToAgeGroup` (add players).
 */
@Composable
fun AgeGroupDetailsScreen(
    ageGroupId: String,
    onNavigateBack: () -> Unit,
    onNavigateToTeam: (teamId: String) -> Unit,
    // "See full stats" on the Stats tab: the Full stats table for the whole age group.
    onNavigateToFullStats: (ageGroupId: String) -> Unit = {},
    onNavigateToPlayer: (playerId: String) -> Unit,
    onNavigateToMatch: (matchId: String) -> Unit,
    onNavigateToAddPlayers: (clubId: String, clubName: String, ageGroupId: String) -> Unit,
    onNavigateToAddStaff: (clubId: String, clubName: String, ageGroupId: String) -> Unit,
    onNavigateToCreateTeams: (clubId: String, clubName: String, sport: SportType) -> Unit,
    onNavigateToCreateMatch: (ageGroupId: String) -> Unit,
    // A staff row opens Member details (design 35) -- coordinators only, who can read it.
    onNavigateToMember: (clubId: String, userId: String) -> Unit = { _, _ -> },
    onNavigateToEditAgeGroup: (ageGroupId: String) -> Unit = {},
    // The coach app's owner, from ⋮: the age group's invite codes.
    onNavigateToInviteStaff: (ageGroupId: String, ageGroupName: String, spaceName: String) -> Unit = { _, _, _ -> },
    // Training plans (design 40): open or edit the season / a month / a week of this age group.
    onNavigateToSeasonPlan: (ageGroupId: String, edit: Boolean) -> Unit = { _, _ -> },
    onNavigateToMonthPlan: (ageGroupId: String, month: LocalDate, edit: Boolean) -> Unit = { _, _, _ -> },
    onNavigateToWeekPlan: (ageGroupId: String, monday: LocalDate, edit: Boolean) -> Unit = { _, _, _ -> },
    onNavigateToCreateSessions: (ageGroupId: String) -> Unit = {},
    onNavigateToAddSession: (ageGroupId: String, date: LocalDate?) -> Unit = { _, _ -> },
    onNavigateToSession: (sessionId: String) -> Unit = {},
    refreshOnReturn: Boolean = false,
    // How many sessions Create sessions just made: "24 sessions created", shown once.
    sessionsCreated: Int? = null,
    onMessageShown: () -> Unit = {},
    viewModel: AgeGroupDetailsViewModel = koinViewModel()
) {
    SquadifyClubTheme {
        val snackbarHostState = remember { SnackbarHostState() }
        val message = sessionsCreated?.let { stringResource(Res.string.msg_sessions_created, it) }
        LaunchedEffect(message) {
            if (message != null) {
                onMessageShown()
                snackbarHostState.showSnackbar(message)
            }
        }
        val uiState = viewModel.state.collectSettledState()
        val recordState by viewModel.record.collectAsStateWithLifecycle()
        val plansState by viewModel.plans.collectAsStateWithLifecycle()
        val sessionsState by viewModel.sessions.collectAsStateWithLifecycle()
        val fullStats by viewModel.fullStats.collectAsStateWithLifecycle()
        val tab by viewModel.tab.collectAsStateWithLifecycle()
        val matchesTeamId by viewModel.matchesTeamId.collectAsStateWithLifecycle()
        val sessionsCalendar by viewModel.sessionsCalendar.collectAsStateWithLifecycle()
        var searchQuery by remember { mutableStateOf("") }

        LaunchedEffect(ageGroupId) { viewModel.load(ageGroupId) }
        val removedText = Res.string.msg_player_removed_age_group
        LaunchedEffect(viewModel) {
            viewModel.playerRemoval.collect { result ->
                snackbarHostState.showSnackbar(
                    when (result) {
                        is PlayerRemovalResult.Removed -> getString(removedText, result.playerName, result.ageGroupName)
                        is PlayerRemovalResult.Failed -> result.message
                    }
                )
            }
        }
        val staffRemovedText = Res.string.msg_staff_removed
        LaunchedEffect(viewModel) {
            viewModel.staffRemoval.collect { result ->
                snackbarHostState.showSnackbar(
                    when (result) {
                        is StaffRemovalResult.Removed -> getString(staffRemovedText, result.name, result.ageGroupName)
                        is StaffRemovalResult.Failed -> result.message
                    }
                )
            }
        }
        LaunchedEffect(refreshOnReturn) { if (refreshOnReturn) viewModel.load(ageGroupId, forceReload = true) }
        // Its match lists have no live socket: refreshed when the app or this screen is shown again.
        OnReturnToApp { viewModel.refreshMatches(ageGroupId) }
        val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
        val refreshFailedText = Res.string.msg_refresh_failed
        LaunchedEffect(viewModel) {
            viewModel.refreshFailed.collect { snackbarHostState.showSnackbar(getString(refreshFailedText)) }
        }

        // The plan boxes in it: See plans only in the coach's own space.
        ShownSpace((uiState as? AgeGroupDetailsUiState.Idle)?.details?.club?.id) {
            AgeGroupDetailsContent(
                uiState = uiState,
                showTraining = viewModel.showsTraining,
                recordState = recordState,
                plansState = plansState,
                sessionsState = sessionsState,
                fullStats = fullStats,
                snackbarHostState = snackbarHostState,
                tab = tab,
                matchesTeamId = matchesTeamId,
                sessionsCalendar = sessionsCalendar,
                searchQuery = searchQuery,
                onTabSelected = viewModel::onTabSelected,
                onFilterSelected = viewModel::onFilterSelected,
                onMatchesTeamSelected = viewModel::onMatchesTeamSelected,
                onSessionsCalendar = viewModel::onSessionsCalendar,
                onSearchChange = { searchQuery = it },
                onNavigateBack = onNavigateBack,
                onRetry = { viewModel.load(ageGroupId, forceReload = true) },
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refresh(ageGroupId) },
                onRetryRecord = { viewModel.loadRecord(ageGroupId) },
                onRetryPlans = { viewModel.loadPlans(ageGroupId) },
                onRetrySessions = { viewModel.loadSessions(ageGroupId) },
                onCreateSessions = { onNavigateToCreateSessions(ageGroupId) },
                onAddSession = { date -> onNavigateToAddSession(ageGroupId, date) },
                onOpenSession = onNavigateToSession,
                onSeasonPlan = { edit -> onNavigateToSeasonPlan(ageGroupId, edit) },
                onMonthPlan = { month, edit -> onNavigateToMonthPlan(ageGroupId, month, edit) },
                onWeekPlan = { monday, edit -> onNavigateToWeekPlan(ageGroupId, monday, edit) },
                onPlayerClick = onNavigateToPlayer,
                onTeamClick = onNavigateToTeam,
                onOpenFullStats = { onNavigateToFullStats(ageGroupId) },
                onMatchClick = onNavigateToMatch,
                onAddPlayers = { d -> onNavigateToAddPlayers(d.ageGroup.clubId, d.club.name, d.ageGroup.id) },
                onAddStaff = { d -> onNavigateToAddStaff(d.ageGroup.clubId, d.club.name, d.ageGroup.id) },
                onCreateTeam = { d -> onNavigateToCreateTeams(d.ageGroup.clubId, d.club.name, d.club.sport) },
                onCreateMatch = { d -> onNavigateToCreateMatch(d.ageGroup.id) },
                onStaffClick = { d, userId -> onNavigateToMember(d.ageGroup.clubId, userId) },
                onEditAgeGroup = { d -> onNavigateToEditAgeGroup(d.ageGroup.id) },
                onRemovePlayer = { playerId -> viewModel.removePlayer(ageGroupId, playerId) },
                onInviteStaff = { d -> onNavigateToInviteStaff(d.ageGroup.id, d.ageGroup.name, d.club.name) },
                onRemoveStaff = { userId -> viewModel.removeStaff(ageGroupId, userId) }
            )
        }
    }
}

private val AgeGroupTab.icon
    get() = when (this) {
        AgeGroupTab.STATS -> Res.drawable.ic_stats
        AgeGroupTab.TEAMS -> Res.drawable.ic_teams
        AgeGroupTab.MATCHES -> Res.drawable.ic_calendar
        AgeGroupTab.TRAINING -> Res.drawable.ic_training
        AgeGroupTab.MEMBERS -> Res.drawable.ic_player
    }

private val AgeGroupTab.label
    get() = when (this) {
        AgeGroupTab.STATS -> Res.string.team_tab_stats
        AgeGroupTab.TEAMS -> Res.string.tab_teams
        AgeGroupTab.MATCHES -> Res.string.team_tab_matches
        AgeGroupTab.TRAINING -> Res.string.tab_training
        AgeGroupTab.MEMBERS -> Res.string.team_tab_players
    }


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgeGroupDetailsContent(
    uiState: AgeGroupDetailsUiState,
    recordState: AgeGroupRecordUiState,
    // False in the coach app: no Training tab.
    showTraining: Boolean = true,
    plansState: AgeGroupPlansUiState = AgeGroupPlansUiState.Loading,
    sessionsState: AgeGroupSessionsUiState = AgeGroupSessionsUiState.Loading,
    fullStats: com.ricardofrango.squadify.model.FullStatsData? = null,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    tab: AgeGroupTab,
    matchesTeamId: String?,
    searchQuery: String,
    onTabSelected: (AgeGroupTab) -> Unit,
    onFilterSelected: (StatsFilter) -> Unit,
    onMatchesTeamSelected: (String?) -> Unit,
    // The Training tab's List / Calendar, remembered on the device.
    sessionsCalendar: Boolean = false,
    onSessionsCalendar: (Boolean) -> Unit = {},
    onSearchChange: (String) -> Unit,
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit,
    // Pull-to-refresh: the content stays under the small indicator.
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onRetryRecord: () -> Unit,
    onRetryPlans: () -> Unit = {},
    onRetrySessions: () -> Unit = {},
    onCreateSessions: () -> Unit = {},
    onAddSession: (LocalDate?) -> Unit = {},
    onOpenSession: (String) -> Unit = {},
    onSeasonPlan: (edit: Boolean) -> Unit = {},
    onMonthPlan: (LocalDate, edit: Boolean) -> Unit = { _, _ -> },
    onWeekPlan: (LocalDate, edit: Boolean) -> Unit = { _, _ -> },
    onPlayerClick: (String) -> Unit,
    onTeamClick: (String) -> Unit,
    onOpenFullStats: () -> Unit = {},
    onMatchClick: (String) -> Unit,
    onAddPlayers: (AgeGroupDetails) -> Unit,
    onAddStaff: (AgeGroupDetails) -> Unit,
    onCreateTeam: (AgeGroupDetails) -> Unit,
    onCreateMatch: (AgeGroupDetails) -> Unit,
    onStaffClick: (AgeGroupDetails, String) -> Unit = { _, _ -> },
    onEditAgeGroup: (AgeGroupDetails) -> Unit = {},
    onRemovePlayer: (playerId: String) -> Unit = {},
    onInviteStaff: (AgeGroupDetails) -> Unit = {},
    onRemoveStaff: (userId: String) -> Unit = {}
) {
    val details = (uiState as? AgeGroupDetailsUiState.Idle)?.details
    val invitesStaff = (uiState as? AgeGroupDetailsUiState.Idle)?.invitesStaff == true
    // Each tab keeps its own scroll; tapping the active tab scrolls it to the top (33f).
    val tabs = remember(showTraining) { AgeGroupTab.entries.filter { showTraining || it != AgeGroupTab.TRAINING } }
    val tabItems = remember(tabs) { tabs.map { DetailTab(it.icon, it.label) } }
    val listStates = AgeGroupTab.entries.associateWith { rememberLazyListState() }
    val scope = rememberCoroutineScope()
    val selectTab: (AgeGroupTab) -> Unit = { t ->
        if (t == tab) scope.launch { listStates.getValue(t).animateScrollToItem(0) } else onTabSelected(t)
    }
    val selectIndex: (Int) -> Unit = { selectTab(tabs[it]) }
    val tabIndex = tabs.indexOf(tab).coerceAtLeast(0)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = isLandscapeLayout
        val rail = useNavigationRail()
        Row(Modifier.fillMaxSize()) {
            if (rail && details != null) DetailRail(tabItems, tabIndex, selectIndex)
            Scaffold(
                contentWindowInsets = ScreenWindowInsets,
                modifier = Modifier.weight(1f).besideRail(rail && details != null),
                containerColor = ScreenBackground,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    Column(Modifier.background(ClubChrome).safeSidesPadding()) {
                        SquadifyTopBar(
                            title = details?.ageGroup?.name ?: stringResource(Res.string.tab_age_groups),
                            subtitle = details?.let { stringResource(Res.string.ag_born, bornLabel(it.ageGroup), it.season.name) },
                            onNavigateBack = onNavigateBack,
                            actions = { if (details != null) OverflowMenu(details, invitesStaff, onAddPlayers, onAddStaff, onCreateTeam, onCreateMatch, onEditAgeGroup, onInviteStaff) }
                        )
                        if (details != null) AgeGroupStrip(details, selectTab)
                    }
                },
                bottomBar = { if (!rail && details != null) DetailBottomBar(tabItems, tabIndex, selectIndex) }
            ) { paddingValues ->
                val boxModifier = Modifier.fillMaxSize().padding(paddingValues)
                when (uiState) {
                    is AgeGroupDetailsUiState.Loading ->
                        Box(boxModifier, contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PitchGreen) }

                    is AgeGroupDetailsUiState.Error ->
                        ErrorScreen(title = uiState.message, body = null, onRetry = onRetry, modifier = boxModifier)

                    is AgeGroupDetailsUiState.Idle -> {
                        val d = uiState.details
                        val opponentsById = remember(d.opponents) { d.opponents.associateBy { it.id } }
                        val teamsById = remember(d.teams) { d.teams.associateBy { it.id } }
                        val next = d.upcomingMatches.firstOrNull { it.status == MatchStatus.SCHEDULED }
                        val nextHint = next?.let {
                            stringResource(
                                Res.string.ag_no_results_hint,
                                teamsById[it.teamId]?.name ?: "—",
                                dateLabel(it.dateTime),
                                opponentsById[it.opponentId]?.let { o -> o.shortName ?: o.name } ?: "—"
                            )
                        }
                        val padding = ScreenPadding.Values
                        PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = boxModifier) {
                            val tabModifier = Modifier.fillMaxSize()
                            when (tab) {
                                AgeGroupTab.STATS -> AgeGroupStatsTab(
                                    details = d, recordState = recordState, listState = listStates.getValue(tab), padding = padding, modifier = tabModifier,
                                    nextHint = nextHint, onViewNext = next?.let { { onMatchClick(it.id) } }, sideBySide = landscape,
                                    onGoToTeams = { onTabSelected(AgeGroupTab.TEAMS) }, onRetry = onRetryRecord,
                                    onFilterSelected = onFilterSelected, onOpenPlayer = onPlayerClick, onOpenMatch = onMatchClick,
                                    fullStats = fullStats,
                                    onOpenFullStats = onOpenFullStats
                                )
                                AgeGroupTab.TEAMS -> AgeGroupTeamsTab(
                                    details = d, recordState = recordState, listState = listStates.getValue(tab), padding = padding, modifier = tabModifier,
                                    nextLabel = { m -> "${dateLabel(m.dateTime)} vs ${opponentsById[m.opponentId]?.let { o -> o.shortName ?: o.name } ?: "—"}" },
                                    onCreateTeam = { onCreateTeam(d) }, onOpenTeam = onTeamClick
                                )
                                AgeGroupTab.MATCHES -> AgeGroupMatchesTab(
                                    details = d, teamFilter = matchesTeamId, listState = listStates.getValue(tab), padding = padding, modifier = tabModifier,
                                    onTeamFilter = onMatchesTeamSelected, onSchedule = { onCreateMatch(d) }, onOpenMatch = onMatchClick
                                )
                                AgeGroupTab.TRAINING -> AgeGroupTrainingTab(
                                    plansState = plansState, listState = listStates.getValue(tab), padding = padding, modifier = tabModifier,
                                    sessionsState = sessionsState, poolSize = d.players.size,
                                    calendar = sessionsCalendar, onCalendar = onSessionsCalendar,
                                    onRetrySessions = onRetrySessions, onCreateSessions = onCreateSessions, onAddSession = onAddSession, onOpenSession = onOpenSession,
                                    onRetry = onRetryPlans,
                                    onOpenSeason = { onSeasonPlan(false) }, onEditSeason = { onSeasonPlan(true) },
                                    onOpenMonth = { onMonthPlan(it, false) }, onEditMonth = { onMonthPlan(it, true) },
                                    onOpenWeek = { onWeekPlan(it, false) }, onEditWeek = { onWeekPlan(it, true) }
                                )
                                AgeGroupTab.MEMBERS -> AgeGroupMembersTab(
                                    details = d, recordState = recordState, searchQuery = searchQuery, listState = listStates.getValue(tab), padding = padding,
                                    modifier = tabModifier, onSearchChange = onSearchChange, onAddStaff = { onAddStaff(d) },
                                    onAddPlayers = { onAddPlayers(d) }, onOpenPlayer = onPlayerClick,
                                    onOpenStaff = if (d.canAddStaff) { userId -> onStaffClick(d, userId) } else null,
                                    // The same permission as adding players to the pool.
                                    onRemovePlayer = if (d.canAssignPlayersToAgeGroup) onRemovePlayer else null,
                                    // The coach app's owner, or a club's admins and coordinators (the server's canAddStaff).
                                    onRemoveStaff = if (uiState.invitesStaff || d.canAddStaff) onRemoveStaff else null,
                                    invitesStaff = uiState.invitesStaff
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The manager actions, kept in ⋮ on every tab (the "+" links by each heading do the same). */
@Composable
private fun OverflowMenu(
    details: AgeGroupDetails,
    // The coach app's owner: Invite staff, to the age group's invite codes.
    invitesStaff: Boolean,
    onAddPlayers: (AgeGroupDetails) -> Unit,
    onAddStaff: (AgeGroupDetails) -> Unit,
    onCreateTeam: (AgeGroupDetails) -> Unit,
    onCreateMatch: (AgeGroupDetails) -> Unit,
    onEditAgeGroup: (AgeGroupDetails) -> Unit,
    onInviteStaff: (AgeGroupDetails) -> Unit
) {
    val canScheduleFromMenu = details.canManageMatch && details.teams.isNotEmpty()
    if (!(details.canAssignPlayersToAgeGroup || details.canAddStaff || invitesStaff || details.canCreateTeam || canScheduleFromMenu || details.canEditAgeGroup)) return
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painter = painterResource(Res.drawable.ic_more), contentDescription = stringResource(Res.string.label_more_options), tint = Color.White)
        }
        DropdownMenu(open, onDismissRequest = { open = false }, modifier = Modifier.background(Color.White)) {
            if (details.canAssignPlayersToAgeGroup) {
                DropdownMenuItem(text = { Text(stringResource(Res.string.label_add_players_to_age_group), color = DeepCarbon) }, onClick = { open = false; onAddPlayers(details) })
            }
            if (details.canAddStaff) {
                DropdownMenuItem(text = { Text(stringResource(Res.string.label_add_staff_to_age_group), color = DeepCarbon) }, onClick = { open = false; onAddStaff(details) })
            }
            if (invitesStaff) {
                DropdownMenuItem(text = { Text(stringResource(Res.string.label_invite_staff), color = DeepCarbon) }, onClick = { open = false; onInviteStaff(details) })
            }
            if (details.canCreateTeam) {
                DropdownMenuItem(text = { Text(stringResource(Res.string.label_create_team), color = DeepCarbon) }, onClick = { open = false; onCreateTeam(details) })
            }
            if (canScheduleFromMenu) {
                DropdownMenuItem(text = { Text(stringResource(Res.string.label_schedule_match), color = DeepCarbon) }, onClick = { open = false; onCreateMatch(details) })
            }
            if (details.canEditAgeGroup) {
                DropdownMenuItem(text = { Text(stringResource(Res.string.edit_age_group), color = DeepCarbon) }, onClick = { open = false; onEditAgeGroup(details) })
            }
        }
    }
}

/** Teams · Players · Staff · Upcoming -- each switches to its tab (Players and Staff both open Members). */
@Composable
private fun AgeGroupStrip(details: AgeGroupDetails, onSelect: (AgeGroupTab) -> Unit) {
    // No padding around the cells or the lines: they all reach the strip's edges.
    Column(Modifier.fillMaxWidth().background(ClubChrome)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.12f)))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            StripStat(details.teams.size.toString(), stringResource(Res.string.tab_teams), Modifier.weight(1f)) { onSelect(AgeGroupTab.TEAMS) }
            StripDivider()
            StripStat(details.players.size.toString(), stringResource(Res.string.tab_players), Modifier.weight(1f)) { onSelect(AgeGroupTab.MEMBERS) }
            StripDivider()
            StripStat(details.staff.size.toString(), stringResource(Res.string.label_age_group_staff), Modifier.weight(1f)) { onSelect(AgeGroupTab.MEMBERS) }
            StripDivider()
            StripStat(details.upcomingMatches.size.toString(), stringResource(Res.string.label_upcoming), Modifier.weight(1f), highlight = details.upcomingMatches.isNotEmpty()) { onSelect(AgeGroupTab.MATCHES) }
        }
    }
}

@Composable
private fun StripStat(value: String, label: String, modifier: Modifier, highlight: Boolean = false, onClick: () -> Unit) {
    // The whole cell is the tap target, down to the strip's bottom edge, so its ripple fills it.
    Column(modifier.clickable(onClick = onClick).padding(top = 9.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, fontSize = 17.sp), color = if (highlight) ChromeHighlight else Color.White)
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp), color = Color.White.copy(alpha = 0.62f), textAlign = TextAlign.Center)
    }
}

@Composable
private fun StripDivider() {
    // Runs the strip's full height, down to its bottom edge.
    Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.12f)))
}

/** "2012", or "2011–2012" for an age group spanning two birth years. */
private fun bornLabel(ag: AgeGroup): String =
    if (ag.startYear == ag.endYear) ag.endYear.toString() else "${minOf(ag.startYear, ag.endYear)}–${maxOf(ag.startYear, ag.endYear)}"

/** "Sat 4 Oct" / "Sáb 4 out.". */
@Composable
private fun dateLabel(instant: Instant): String =
    weekdayDateLabel(instant.toLocalDateTime(TimeZone.currentSystemDefault()).date)

// ---- Previews ----

private fun sampleDetails(populated: Boolean, canManage: Boolean): AgeGroupDetails {
    val ag = AgeGroup(id = "ag1", clubId = "c1", name = "Sub 2015", seasonId = "s1", startYear = 2015, endYear = 2015)
    val club = Club(id = "c1", name = "CD Ribamar", status = ClubStatus.ACTIVE, sport = SportType.FOOTBALL, createdAt = Instant.fromEpochMilliseconds(0))
    val season = Season(id = "s1", clubId = "c1", name = "26/27", type = SeasonType.EUROPEAN_YEAR, startDate = LocalDate(2026, 7, 1), endDate = LocalDate(2027, 6, 30), active = true)
    fun p(id: String, f: String, l: String, y: Int, n: Int?, pos: PlayerPosition? = null) =
        Player(id, "c1", f, l, LocalDate(y, 3, 1), n, null, true, position = pos)
    return AgeGroupDetails(
        ageGroup = ag, club = club, season = season,
        players = if (populated) listOf(
            p("p1", "Afonso", "Rebelo", 2015, 7, PlayerPosition.MIDFIELD),
            p("p2", "Bruno", "Tavares", 2015, 4),
            p("p3", "Caetano", "Lousada", 2016, 11, PlayerPosition.STRIKER),
            // Disabled in the club: listed in its own section of Members.
            p("p4", "Duarte", "Meireles", 2015, 9, PlayerPosition.DEFENDER).copy(active = false)
        ) else emptyList(),
        staff = if (populated) listOf(
            AgeGroupStaffMember("u1", "Nuno", "Faria", AgeGroupRole.COACH),
            AgeGroupStaffMember("u2", "Rita", "Sousa", AgeGroupRole.STAFF)
        ) else emptyList(),
        teams = if (populated) listOf(
            Team("t1", "c1", "ag1", "s1", "Sub 2015 A", "A", true),
            Team("t2", "c1", "ag1", "s1", "Sub 2015 B", "B", true)
        ) else emptyList(),
        upcomingMatches = emptyList(),
        opponents = emptyList(),
        canAddStaff = canManage,
        canCreateTeam = canManage,
        canManageMatch = canManage,
        canAssignPlayersToAgeGroup = canManage
    )
}


private fun sampleRecord(details: AgeGroupDetails, staff: Boolean): AgeGroupSeasonRecord {
    val pool = details.players.map { TeamSeasonPlayer(it.id, "${it.firstName} ${it.lastName}", it.shirtNumber, it.position, it.photoUrl) }
    fun m(id: String, day: Int, type: MatchType?, us: Int, them: Int) =
        TeamSeasonMatch(id, Instant.fromEpochMilliseconds(1_790_000_000_000L - day * 86_400_000L), "AD Fontelas", type, us, them)
    val teams = details.teams.mapIndexed { t, team ->
        val matches = listOf(m("${team.id}-1", 2 + t, MatchType.CHAMPIONSHIP, 3 - t, 1), m("${team.id}-2", 9 + t, MatchType.FRIENDLY, 1, 1 + t))
        TeamSeasonRecord(
            teamId = team.id, seasonId = "s1", seasonName = "26/27", matches = matches,
            playerLines = matches.flatMap { match ->
                pool.take(2 + t).mapIndexed { j, p -> TeamSeasonPlayerLine(match.matchId, p.id, 70 - 25 * j, if (j == 0) 1 else 0, 0, if (staff) 6 + j else null, started = j == 0) }
            },
            players = pool, canSeeRatings = staff, teamName = team.name
        )
    }
    return AgeGroupSeasonRecord(details.ageGroup.id, teams, pool, staff)
}

@Composable
private fun AgeGroupPreview(details: AgeGroupDetails, record: AgeGroupRecordUiState, tab: AgeGroupTab, dark: Boolean = false) {
    SquadifyTheme(darkTheme = dark) {
        AgeGroupDetailsContent(
            uiState = AgeGroupDetailsUiState.Idle(details), recordState = record, tab = tab, matchesTeamId = null, searchQuery = "",
            onTabSelected = {}, onFilterSelected = {}, onMatchesTeamSelected = {}, onSearchChange = {},
            onNavigateBack = {}, onRetry = {}, onRetryRecord = {}, onPlayerClick = {}, onTeamClick = {}, onMatchClick = {},
            onAddPlayers = {}, onAddStaff = {}, onCreateTeam = {}, onCreateMatch = {}
        )
    }
}

@Preview
@Composable
private fun AgeGroupStatsPreview() {
    val d = sampleDetails(populated = true, canManage = true)
    AgeGroupPreview(d, AgeGroupRecordUiState.Loaded(sampleRecord(d, staff = true)), AgeGroupTab.STATS)
}

@Preview
@Composable
private fun AgeGroupTeamsPreview() {
    val d = sampleDetails(populated = true, canManage = true)
    AgeGroupPreview(d, AgeGroupRecordUiState.Loaded(sampleRecord(d, staff = true)), AgeGroupTab.TEAMS)
}

@Preview
@Composable
private fun AgeGroupMembersParentPreview() {
    val d = sampleDetails(populated = true, canManage = false)
    AgeGroupPreview(d, AgeGroupRecordUiState.Loaded(sampleRecord(d, staff = false)), AgeGroupTab.MEMBERS)
}

@Preview
@Composable
private fun AgeGroupEmptyPreview() {
    val d = sampleDetails(populated = false, canManage = true)
    AgeGroupPreview(d, AgeGroupRecordUiState.Loaded(AgeGroupSeasonRecord("ag1", emptyList(), emptyList(), true)), AgeGroupTab.STATS, dark = true)
}

@Preview(widthDp = 780, heightDp = 360)
@Composable
private fun AgeGroupLandscapePreview() {
    val d = sampleDetails(populated = true, canManage = true)
    AgeGroupPreview(d, AgeGroupRecordUiState.Loaded(sampleRecord(d, staff = true)), AgeGroupTab.STATS)
}
