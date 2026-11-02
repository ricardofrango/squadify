package com.ricardofrango.squadify.presentation.ui.teamdetails

import androidx.compose.runtime.remember
import org.jetbrains.compose.resources.getString
import squadify.app.shared.generated.resources.msg_refresh_failed
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.ricardofrango.squadify.presentation.ui.components.OnReturnToApp
import com.ricardofrango.squadify.presentation.ui.components.useNavigationRail
import com.ricardofrango.squadify.presentation.ui.components.isLandscapeLayout
import com.ricardofrango.squadify.presentation.ui.theme.ClubChrome
import com.ricardofrango.squadify.presentation.ui.theme.ChromeHighlight
import com.ricardofrango.squadify.presentation.ui.components.emptySquadRes
import com.ricardofrango.squadify.presentation.navigation.collectSettledState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricardofrango.squadify.model.*
import com.ricardofrango.squadify.presentation.ui.components.DetailBottomBar
import com.ricardofrango.squadify.presentation.ui.components.DetailRail
import com.ricardofrango.squadify.presentation.ui.components.DetailTab
import com.ricardofrango.squadify.presentation.ui.components.SquadifyTopBar
import com.ricardofrango.squadify.presentation.ui.components.MatchRow
import com.ricardofrango.squadify.presentation.ui.components.StatsFilterSegments
import com.ricardofrango.squadify.presentation.ui.components.StatsHeading
import com.ricardofrango.squadify.presentation.ui.components.MatchListEntry
import com.ricardofrango.squadify.presentation.ui.components.MatchListSection
import com.ricardofrango.squadify.presentation.ui.components.matchListSections
import com.ricardofrango.squadify.presentation.ui.components.StatsMessage
import com.ricardofrango.squadify.presentation.ui.components.StatsCard
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.ScreenPadding
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyClubTheme
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.ScreenBackground
import com.ricardofrango.squadify.presentation.ui.theme.screenContentPadding
import squadify.app.shared.generated.resources.*
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Instant

private val CardShape = RoundedCornerShape(16.dp)

private val WeekdayDayMonth = LocalDate.Format {
    dayOfWeek(DayOfWeekNames.ENGLISH_ABBREVIATED); char(' '); day(Padding.NONE); char(' '); monthName(MonthNames.ENGLISH_ABBREVIATED)
}

/**
 * The hub for one team (design 32): Stats · Matches · Members tabs under a fixed header (name, the
 * Next fixture, Upcoming · Played · Players used). A team is a scheduling handle under an age group,
 * so the Members tab lists only the players who actually played for THIS team, with the age group's
 * staff and pool as context under them. It always opens on Matches. From a medium-width window up the bottom bar becomes a side rail (32e). Manage affordances are
 * gated on [TeamDetails.canManageMatch].
 */
@Composable
fun TeamDetailsScreen(
    teamId: String,
    onNavigateBack: () -> Unit,
    onNavigateToAgeGroup: (ageGroupId: String) -> Unit,
    // From the leaders or the Members tab: the player opens on this team's season and the current filter.
    onNavigateToPlayer: (playerId: String, seasonId: String, filter: StatsFilter) -> Unit,
    onNavigateToMatch: (String) -> Unit,
    onScheduleMatch: () -> Unit,
    onEditTeam: () -> Unit,
    // "See full stats" under Team leaders: the Full stats table for this team.
    onOpenFullStats: () -> Unit = {},
    // A staff chip opens Member details (design 35) -- only for coordinators ([TeamDetails.canViewMembers]).
    onNavigateToMember: (clubId: String, userId: String) -> Unit = { _, _ -> },
    refreshOnReturn: Boolean = false,
    viewModel: TeamDetailsViewModel = koinViewModel()
) {
    SquadifyClubTheme {
        val uiState = viewModel.state.collectSettledState()
        val recordState by viewModel.record.collectAsStateWithLifecycle()
        val tab by viewModel.tab.collectAsStateWithLifecycle()
        val squadSort by viewModel.squadSort.collectAsStateWithLifecycle()

        LaunchedEffect(teamId) { viewModel.load(teamId) }
        LaunchedEffect(refreshOnReturn) { if (refreshOnReturn) viewModel.load(teamId, forceReload = true) }
        // Its match lists have no live socket: refreshed when the app or this screen is shown again.
        OnReturnToApp { viewModel.refreshMatches(teamId) }
        val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
        val snackbarHostState = remember { SnackbarHostState() }
        val refreshFailedText = Res.string.msg_refresh_failed
        LaunchedEffect(viewModel) {
            viewModel.refreshFailed.collect { snackbarHostState.showSnackbar(getString(refreshFailedText)) }
        }

        TeamDetailsContent(
            uiState = uiState,
            recordState = recordState,
            tab = tab,
            squadSort = squadSort,
            onTabSelected = viewModel::onTabSelected,
            onSquadSortSelected = viewModel::onSquadSortSelected,
            onNavigateBack = onNavigateBack,
            onRetry = { viewModel.load(teamId, forceReload = true) },
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh(teamId) },
            snackbarHostState = snackbarHostState,
            onRetryRecord = { viewModel.loadRecord(teamId) },
            onFilterSelected = viewModel::onFilterSelected,
            onNavigateToAgeGroup = onNavigateToAgeGroup,
            onNavigateToPlayer = onNavigateToPlayer,
            onScheduleMatch = onScheduleMatch,
            onEditTeam = onEditTeam,
            onOpenFullStats = onOpenFullStats,
            onMatchClick = onNavigateToMatch,
            onNavigateToMember = onNavigateToMember
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeamDetailsContent(
    uiState: TeamDetailsUiState,
    recordState: TeamRecordUiState,
    tab: TeamTab,
    squadSort: SquadSort,
    onTabSelected: (TeamTab) -> Unit,
    onSquadSortSelected: (SquadSort) -> Unit,
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit,
    // Pull-to-refresh: the content stays under the small indicator.
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onRetryRecord: () -> Unit,
    onFilterSelected: (StatsFilter) -> Unit,
    onNavigateToAgeGroup: (String) -> Unit,
    onNavigateToPlayer: (String, String, StatsFilter) -> Unit,
    onScheduleMatch: () -> Unit,
    onEditTeam: () -> Unit,
    onMatchClick: (String) -> Unit,
    onOpenFullStats: () -> Unit = {},
    onNavigateToMember: (String, String) -> Unit = { _, _ -> }
) {
    val details = (uiState as? TeamDetailsUiState.Idle)?.details
    // Each tab keeps its own scroll position; tapping the active tab scrolls it to the top (32f).
    val listStates = TeamTab.entries.associateWith { rememberLazyListState() }
    val scope = rememberCoroutineScope()
    val selectTab: (TeamTab) -> Unit = { t ->
        if (t == tab) scope.launch { listStates.getValue(t).animateScrollToItem(0) } else onTabSelected(t)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = isLandscapeLayout
        val rail = useNavigationRail()
        Row(Modifier.fillMaxSize()) {
            if (rail && details != null) TeamRail(tab, selectTab)
            Scaffold(
                modifier = Modifier.weight(1f),
                containerColor = ScreenBackground,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    Column(Modifier.background(ClubChrome)) {
                        SquadifyTopBar(
                            title = details?.team?.name ?: stringResource(Res.string.title_team_details),
                            // Landscape (32e): the Next fixture folds into the subtitle to save height.
                            subtitle = details?.let { d ->
                                val base = "${d.ageGroup.name} · ${d.season.name}"
                                val next = d.upcomingMatches.firstOrNull()
                                if (landscape && next != null) {
                                    "$base · ${stringResource(Res.string.label_next)} ${dateLabel(next.dateTime)} vs ${opponentName(d.opponents.associateBy { it.id }, next.opponentId)}"
                                } else base
                            },
                            onNavigateBack = onNavigateBack,
                            actions = {
                                if (details != null && (details.canManageMatch || details.canEditTeam)) {
                                    var open by remember { mutableStateOf(false) }
                                    Box {
                                        IconButton(onClick = { open = true }) {
                                            Icon(
                                                painter = painterResource(Res.drawable.ic_more),
                                                contentDescription = stringResource(Res.string.label_more_options),
                                                tint = Color.White
                                            )
                                        }
                                        DropdownMenu(open, onDismissRequest = { open = false }, modifier = Modifier.background(Color.White)) {
                                            if (details.canManageMatch) {
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(Res.string.label_schedule_match), color = DeepCarbon) },
                                                    onClick = { open = false; onScheduleMatch() }
                                                )
                                            }
                                            if (details.canEditTeam) {
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(Res.string.label_edit_team), color = DeepCarbon) },
                                                    onClick = { open = false; onEditTeam() }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        )
                        if (details != null) {
                            TeamHeader(details, recordState, showNext = !landscape, onSelect = selectTab)
                        }
                    }
                },
                bottomBar = {
                    if (!rail && details != null) TeamBottomBar(tab, selectTab)
                }
            ) { paddingValues ->
                val boxModifier = Modifier.fillMaxSize().padding(paddingValues)
                when (uiState) {
                    is TeamDetailsUiState.Loading ->
                        Box(boxModifier, contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PitchGreen) }

                    is TeamDetailsUiState.Error ->
                        Column(boxModifier.screenContentPadding(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(uiState.message, color = MaterialTheme.colorScheme.error, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = onRetry) { Text(stringResource(Res.string.btn_retry)) }
                        }

                    is TeamDetailsUiState.Idle -> {
                        val d = uiState.details
                        val opponentsById = remember(d.opponents) { d.opponents.associateBy { it.id } }
                        val next = d.upcomingMatches.firstOrNull { it.status == MatchStatus.SCHEDULED }
                        val nextHint = next?.let {
                            stringResource(Res.string.team_first_match_hint, dateLabel(it.dateTime), opponentName(opponentsById, it.opponentId))
                        }
                        val padding = ScreenPadding.Values
                        val listModifier = Modifier.fillMaxSize()
                        PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = boxModifier) {
                            when (tab) {
                                TeamTab.STATS -> LazyColumn(listModifier, state = listStates.getValue(TeamTab.STATS), contentPadding = padding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    item(key = "record") {
                                        TeamRecordCard(
                                            state = recordState,
                                            teamName = d.team.name,
                                            nextHint = nextHint,
                                            onViewNext = next?.let { { onMatchClick(it.id) } },
                                            onRetry = onRetryRecord,
                                            onFilterSelected = onFilterSelected,
                                            onOpenMatch = onMatchClick
                                        )
                                    }
                                    val loaded = recordState as? TeamRecordUiState.Loaded
                                    if (loaded != null && loaded.hasLeaders()) {
                                        item(key = "leaders") {
                                            TeamLeadersCard(loaded, onSeeAll = onOpenFullStats) { playerId -> onNavigateToPlayer(playerId, loaded.record.seasonId, loaded.filter) }
                                        }
                                    }
                                }
    
                                TeamTab.MATCHES -> MatchesTab(
                                    details = d,
                                    opponentsById = opponentsById,
                                    listState = listStates.getValue(TeamTab.MATCHES),
                                    padding = padding,
                                    modifier = listModifier,
                                    onScheduleMatch = onScheduleMatch,
                                    onMatchClick = onMatchClick
                                )
    
                                TeamTab.PLAYERS -> PlayersTab(
                                    details = d,
                                    recordState = recordState,
                                    squadSort = squadSort,
                                    nextHint = nextHint,
                                    onViewNext = next?.let { { onMatchClick(it.id) } },
                                    listState = listStates.getValue(TeamTab.PLAYERS),
                                    padding = padding,
                                    modifier = listModifier,
                                    onFilterSelected = onFilterSelected,
                                    onSquadSortSelected = onSquadSortSelected,
                                    onRetry = onRetryRecord,
                                    onOpenPlayer = { playerId, seasonId, filter -> onNavigateToPlayer(playerId, seasonId, filter) },
                                    onViewPool = { onNavigateToAgeGroup(d.ageGroup.id) },
                                    onOpenStaff = if (d.canViewMembers) { userId -> onNavigateToMember(d.team.clubId, userId) } else null
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- Header ----

/**
 * Under the top bar on every tab: the Next fixture, then Upcoming · Played · Players used -- each
 * number switches to its tab. Pool and Staff left the strip (they belong to the age group; they're
 * at the bottom of the Members tab now). "Players used" shows "—" while unknown or if it failed.
 */
@Composable
private fun TeamHeader(details: TeamDetails, recordState: TeamRecordUiState, showNext: Boolean, onSelect: (TeamTab) -> Unit) {
    val opponentsById = remember(details.opponents) { details.opponents.associateBy { it.id } }
    val next = details.upcomingMatches.firstOrNull()
    val playersUsed = (recordState as? TeamRecordUiState.Loaded)?.record?.squad(StatsFilter.ALL)?.used?.size
    // No padding around the stat cells or the lines: they all reach the strip's edges. Only the Next
    // card keeps its inset.
    Column(Modifier.fillMaxWidth().background(ClubChrome)) {
        if (next != null && showNext) {
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 4.dp).background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(Res.string.label_next).uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.5.sp), color = ChromeHighlight)
                Spacer(Modifier.width(10.dp))
                Text(
                    "${dateLabel(next.dateTime)} · vs ${opponentName(opponentsById, next.opponentId)}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
                    color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.12f)))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            StripStat(details.upcomingMatches.size.toString(), stringResource(Res.string.label_upcoming), highlight = details.upcomingMatches.isNotEmpty(), modifier = Modifier.weight(1f)) { onSelect(TeamTab.MATCHES) }
            StripDivider()
            StripStat(details.recentResults.size.toString(), stringResource(Res.string.label_played), modifier = Modifier.weight(1f)) { onSelect(TeamTab.STATS) }
            StripDivider()
            StripStat(playersUsed?.toString() ?: "—", stringResource(Res.string.team_players_used), modifier = Modifier.weight(1f)) { onSelect(TeamTab.PLAYERS) }
        }
    }
}

@Composable
private fun StripStat(value: String, label: String, modifier: Modifier, highlight: Boolean = false, onClick: () -> Unit) {
    // The whole cell is the tap target, down to the strip's bottom edge, so its ripple fills it.
    Column(modifier.clickable(onClick = onClick).padding(top = 9.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, fontSize = 17.sp), color = if (highlight) ChromeHighlight else Color.White)
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp), color = Color.White.copy(alpha = 0.62f), maxLines = 1)
    }
}

@Composable
private fun StripDivider() {
    // Runs the strip's full height, down to its bottom edge.
    Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.12f)))
}

// ---- Navigation ----

private val TeamTab.icon: DrawableResource
    get() = when (this) {
        TeamTab.STATS -> Res.drawable.ic_stats
        TeamTab.MATCHES -> Res.drawable.ic_calendar
        TeamTab.PLAYERS -> Res.drawable.ic_teams
    }

private val TeamTab.label
    get() = when (this) {
        TeamTab.STATS -> Res.string.team_tab_stats
        TeamTab.MATCHES -> Res.string.team_tab_matches
        TeamTab.PLAYERS -> Res.string.team_tab_players
    }

private val TeamTabs = TeamTab.entries

@Composable
private fun TeamBottomBar(selected: TeamTab, onSelect: (TeamTab) -> Unit) =
    DetailBottomBar(TeamTabs.map { DetailTab(it.icon, it.label) }, TeamTabs.indexOf(selected)) { onSelect(TeamTabs[it]) }

/** Landscape (32e): the tabs move to a left rail. */
@Composable
private fun TeamRail(selected: TeamTab, onSelect: (TeamTab) -> Unit) =
    DetailRail(TeamTabs.map { DetailTab(it.icon, it.label) }, TeamTabs.indexOf(selected)) { onSelect(TeamTabs[it]) }

// ---- Matches tab ----

/** Upcoming and Recent results; no filter here -- the rows carry their type tags and the lists are short. */
@Composable
private fun MatchesTab(
    details: TeamDetails,
    opponentsById: Map<String, Opponent>,
    listState: LazyListState,
    padding: PaddingValues,
    modifier: Modifier,
    onScheduleMatch: () -> Unit,
    onMatchClick: (String) -> Unit
) {
    LazyColumn(modifier, state = listState, contentPadding = padding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        matchListSections(
            notPlayed = MatchListSection(details.notPlayedMatches.map { MatchListEntry(it, null, details.team.name) }),
            upcoming = MatchListSection(details.upcomingMatches.map { MatchListEntry(it, details.upcomingMatchScores[it.id], details.team.name) }),
            results = MatchListSection(details.recentResults.map {
                MatchListEntry(it.match, MatchScore(it.match.id, it.ourScore, it.opponentScore), details.team.name)
            }),
            opponentsById = opponentsById,
            clubLogoUrl = details.club.logoUrl,
            onOpenMatch = onMatchClick,
            onSchedule = if (details.canManageMatch) onScheduleMatch else null
        )
    }
}

// ---- Members tab ----

@Composable
private fun PlayersTab(
    details: TeamDetails,
    recordState: TeamRecordUiState,
    squadSort: SquadSort,
    nextHint: String?,
    onViewNext: (() -> Unit)?,
    listState: LazyListState,
    padding: PaddingValues,
    modifier: Modifier,
    onFilterSelected: (StatsFilter) -> Unit,
    onSquadSortSelected: (SquadSort) -> Unit,
    onRetry: () -> Unit,
    onOpenPlayer: (String, String, StatsFilter) -> Unit,
    onViewPool: () -> Unit,
    onOpenStaff: ((String) -> Unit)? = null
) {
    LazyColumn(modifier, state = listState, contentPadding = padding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (recordState) {
            TeamRecordUiState.Loading -> item {
                Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PitchGreen, modifier = Modifier.size(28.dp))
                }
            }

            TeamRecordUiState.Error -> item {
                StatsCard(contentPadding = 16.dp) {
                    StatsMessage(
                        stringResource(Res.string.squad_load_failed_title),
                        stringResource(Res.string.squad_load_failed_body),
                        onRetry = onRetry
                    )
                }
            }

            is TeamRecordUiState.Loaded -> {
                val record = recordState.record
                if (record.matches.isEmpty()) {
                    // No finished match yet: point at the Next fixture (32c).
                    item {
                        StatsCard(contentPadding = 16.dp) {
                            StatsMessage(
                                stringResource(Res.string.squad_no_players_title),
                                nextHint ?: stringResource(Res.string.record_no_results_body, details.team.name)
                            )
                            if (onViewNext != null) ViewMatchButton(onViewNext)
                        }
                    }
                } else {
                    val squad = record.squad(recordState.filter, squadSort)
                    item { StatsFilterSegments(recordState.filter, onFilterSelected, bottomSpacing = 0.dp) }
                    if (squad.used.isEmpty() && squad.benchOnly.isEmpty()) {
                        item {
                            StatsCard(contentPadding = 16.dp) {
                                StatsMessage(
                                    stringResource(recordState.filter.emptySquadRes()),
                                    stringResource(Res.string.squad_switch_to_all)
                                )
                            }
                        }
                    } else {
                        item { SquadSortChips(squadSort, staff = record.canSeeRatings, onSelected = onSquadSortSelected) }
                        item {
                            SquadCard(squad.used, squad.benchOnly, staff = record.canSeeRatings) { playerId ->
                                onOpenPlayer(playerId, record.seasonId, recordState.filter)
                            }
                        }
                    }
                }
            }
        }
        // The age group's staff and pool stay one tap away on every state of this tab.
        item {
            AgeGroupContextCard(
                staff = details.staff,
                ageGroupName = details.ageGroup.name,
                poolSize = details.poolSize,
                siblingTeamNames = details.siblingTeamNames,
                onViewPool = onViewPool,
                onOpenStaff = onOpenStaff
            )
        }
    }
}

/** Outlined "View match" under an empty state, opening the Next fixture (32c). */
@Composable
internal fun ViewMatchButton(onClick: () -> Unit) {
    Spacer(Modifier.height(12.dp))
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.5.dp, CarbonGray.copy(alpha = 0.2f))) {
        Box(Modifier.heightIn(min = 40.dp).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(Res.string.team_view_match), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp), color = CarbonGray)
        }
    }
}

// ---- Helpers ----

private fun opponentName(opponentsById: Map<String, Opponent>, opponentId: String): String =
    opponentsById[opponentId]?.let { it.shortName ?: it.name } ?: "—"

/** "Sat 4 Oct". */
private fun dateLabel(instant: Instant): String =
    instant.toLocalDateTime(TimeZone.currentSystemDefault()).date.format(WeekdayDayMonth)

// ---- Previews ----

private fun sampleDetails(populated: Boolean, canManage: Boolean): TeamDetails {
    val ag = AgeGroup(id = "ag1", clubId = "c1", name = "Sub-15", seasonId = "s1", startYear = 2011, endYear = 2011)
    val season = Season(id = "s1", clubId = "c1", name = "26/27", type = SeasonType.EUROPEAN_YEAR, startDate = LocalDate(2026, 7, 1), endDate = LocalDate(2027, 6, 30), active = true)
    val team = Team(id = "t1", clubId = "c1", ageGroupId = "ag1", seasonId = "s1", name = "Sub-15 A", code = "S15A", active = true)
    val club = Club(id = "c1", name = "CD Ribamar", status = ClubStatus.ACTIVE, sport = SportType.FOOTBALL, createdAt = Instant.fromEpochMilliseconds(0))
    val opponents = listOf(
        Opponent(id = "o1", clubId = "c1", name = "AD Fontelas", shortName = "AD Fontelas"),
        Opponent(id = "o2", clubId = "c1", name = "CD Aveleda", shortName = "CD Aveleda")
    )
    val base = Instant.fromEpochMilliseconds(1_760_000_000_000)
    fun match(id: String, oppId: String, status: MatchStatus, location: String?) =
        Match(id = id, clubId = "c1", teamId = "t1", opponentId = oppId, location = location, dateTime = base, status = status)
    return TeamDetails(
        team = team,
        club = club,
        ageGroup = ag,
        season = season,
        poolSize = 14,
        siblingTeamNames = if (populated) listOf("Sub-15 B") else emptyList(),
        staff = if (populated) listOf(
            AgeGroupStaffMember("u1", "João", "Camelo", AgeGroupRole.COACH),
            AgeGroupStaffMember("u2", "Rita", "Sá", AgeGroupRole.COACH),
            AgeGroupStaffMember("u3", "Paulo", "Mesquita", AgeGroupRole.STAFF)
        ) else listOf(AgeGroupStaffMember("u1", "Mário", "Aleixo", AgeGroupRole.COACH)),
        upcomingMatches = if (populated) listOf(
            match("m1", "o1", MatchStatus.SCHEDULED, "Campo do Ribamar"),
            match("m2", "o2", MatchStatus.SCHEDULED, "Away")
        ) else emptyList(),
        recentResults = if (populated) listOf(
            TeamMatchResult(match("r1", "o1", MatchStatus.FINISHED, null), 3, 1, MatchOutcome.WIN),
            TeamMatchResult(match("r2", "o2", MatchStatus.FINISHED, null), 2, 0, MatchOutcome.WIN),
            TeamMatchResult(match("r3", "o1", MatchStatus.FINISHED, null), 1, 1, MatchOutcome.DRAW),
            TeamMatchResult(match("r4", "o2", MatchStatus.FINISHED, null), 0, 2, MatchOutcome.LOSS)
        ) else emptyList(),
        opponents = opponents,
        canManageMatch = canManage
    )
}

private fun sampleRecord(staff: Boolean, empty: Boolean = false): TeamSeasonRecord {
    fun m(id: String, day: Int, type: MatchType?, us: Int, them: Int) =
        TeamSeasonMatch(id, Instant.fromEpochMilliseconds(1_790_000_000_000L - day * 86_400_000L), "AD Fontelas", type, us, them)
    val matches = if (empty) emptyList() else listOf(
        m("r1", 0, MatchType.CHAMPIONSHIP, 3, 1), m("r2", 7, MatchType.CHAMPIONSHIP, 1, 1), m("r3", 13, MatchType.TOURNAMENT, 2, 0),
        m("r4", 14, MatchType.TOURNAMENT, 0, 2), m("r5", 21, MatchType.CHAMPIONSHIP, 2, 1), m("r6", 28, MatchType.FRIENDLY, 3, 3),
        m("r7", 35, null, 2, 2)
    )
    val players = listOf(
        TeamSeasonPlayer("p1", "Tomás Bragança", 10, PlayerPosition.MIDFIELD), TeamSeasonPlayer("p2", "Gil Bettencourt", 6, PlayerPosition.MIDFIELD),
        TeamSeasonPlayer("p3", "Francisco Nabais", 11, PlayerPosition.WINGER), TeamSeasonPlayer("p4", "Bruno Tavares", 9, PlayerPosition.STRIKER),
        TeamSeasonPlayer("p5", "Rui Perestrelo", 1, PlayerPosition.GOALKEEPER), TeamSeasonPlayer("p6", "Caetano Lousada", 14, PlayerPosition.DEFENDER)
    )
    val lines = matches.flatMapIndexed { i, match ->
        players.filter { it.id != "p6" }.mapIndexed { j, p ->
            TeamSeasonPlayerLine(
                match.matchId, p.id, minutesPlayed = 70 - j * 5 + (if (p.id == "p5") 10 else 0),
                goals = if ((i + j) % 3 == 0) 1 else 0, assists = if ((i + j) % 4 == 1) 1 else 0,
                rating = if (staff) 5 + (i + j) % 5 else null,
                started = j < 4
            )
        } + TeamSeasonPlayerLine(match.matchId, "p6", 0, 0, 0) // called up, never on
    }
    return TeamSeasonRecord("t1", "s1", "2026/27", matches, lines, players, canSeeRatings = staff)
}

/** The record as Full stats data, with three trainings (one player missing one), for Team leaders' ranking. */
private fun sampleFullStats(record: TeamSeasonRecord) = FullStatsData(
    title = "Sub-13", ageGroupId = "ag1", seasonId = record.seasonId, seasonName = record.seasonName,
    seasonStart = LocalDate(2026, 9, 1), seasonEnd = LocalDate(2027, 6, 30),
    teams = listOf(FullStatsTeam(record.teamId, "Sub-13 A")),
    matches = record.matches.map { FullStatsMatch(it.matchId, record.teamId, it.dateTime, it.matchType, it.ourScore, it.opponentScore) },
    lines = record.playerLines.map { FullStatsLine(it.matchId, it.playerId, it.started, it.minutesPlayed, it.goals, it.assists, 0, 0, it.rating) },
    trainings = if (!record.canSeeRatings) emptyList() else (1..3).map { d ->
        FullStatsTraining("t$d", LocalDate(2026, 10, d), record.players.mapIndexed { i, p -> TrainingRating(p.id, if (i == 0 && d == 3) 0.0 else 6.0 + (i + d) % 4) })
    },
    players = record.players,
    canSeeRatings = record.canSeeRatings
)


@Composable
private fun TeamPreview(details: TeamDetails, record: TeamRecordUiState, tab: TeamTab = TeamTab.STATS, dark: Boolean = false) {
    SquadifyTheme(darkTheme = dark) {
        TeamDetailsContent(
            uiState = TeamDetailsUiState.Idle(details),
            recordState = (record as? TeamRecordUiState.Loaded)?.let { it.copy(fullStats = sampleFullStats(it.record)) } ?: record,
            tab = tab, squadSort = SquadSort.APPS,
            onTabSelected = {}, onSquadSortSelected = {},
            onNavigateBack = {}, onRetry = {}, onRetryRecord = {}, onFilterSelected = {}, onNavigateToAgeGroup = {},
            onNavigateToPlayer = { _, _, _ -> }, onScheduleMatch = {}, onEditTeam = {}, onMatchClick = {}
        )
    }
}

@Preview
@Composable
private fun TeamDetailsStatsPreview() =
    TeamPreview(sampleDetails(populated = true, canManage = true), TeamRecordUiState.Loaded(sampleRecord(staff = true)))

@Preview
@Composable
private fun TeamDetailsMatchesPreview() =
    TeamPreview(sampleDetails(populated = true, canManage = true), TeamRecordUiState.Loaded(sampleRecord(staff = true)), TeamTab.MATCHES)

@Preview
@Composable
private fun TeamDetailsPlayersStaffPreview() =
    TeamPreview(sampleDetails(populated = true, canManage = true), TeamRecordUiState.Loaded(sampleRecord(staff = true)), TeamTab.PLAYERS)

@Preview
@Composable
private fun TeamDetailsPlayersMemberPreview() =
    TeamPreview(sampleDetails(populated = true, canManage = false), TeamRecordUiState.Loaded(sampleRecord(staff = false)), TeamTab.PLAYERS)

@Preview
@Composable
private fun TeamDetailsPlayersErrorPreview() =
    TeamPreview(sampleDetails(populated = true, canManage = true), TeamRecordUiState.Error, TeamTab.PLAYERS)

@Preview
@Composable
private fun TeamDetailsNoResultsPreview() =
    TeamPreview(sampleDetails(populated = true, canManage = true), TeamRecordUiState.Loaded(sampleRecord(staff = true, empty = true)), TeamTab.PLAYERS, dark = true)
