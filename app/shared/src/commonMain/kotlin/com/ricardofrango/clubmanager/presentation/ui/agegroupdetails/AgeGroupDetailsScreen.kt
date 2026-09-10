package com.ricardofrango.clubmanager.presentation.ui.agegroupdetails

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ricardofrango.clubmanager.model.*
import com.ricardofrango.clubmanager.presentation.ui.components.FMTopBar
import com.ricardofrango.clubmanager.presentation.ui.theme.CarbonGray
import com.ricardofrango.clubmanager.presentation.ui.theme.DeepCarbon
import com.ricardofrango.clubmanager.presentation.ui.theme.ClubManagerTheme
import com.ricardofrango.clubmanager.presentation.ui.theme.PitchGreen
import clubmanager.app.shared.generated.resources.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Instant

private val CardShape = RoundedCornerShape(16.dp)

private val MonthYear = LocalDate.Format {
    monthName(MonthNames.ENGLISH_ABBREVIATED); char(' '); year()
}
private val DayMonth = LocalDate.Format {
    day(); char(' '); monthName(MonthNames.ENGLISH_ABBREVIATED)
}

/**
 * The hub for one age group: its eligibility rule, the counts, then Players / Staff / Teams /
 * Matches. Built to the "Age group details" artboard of the design canvas linked in AI_GUIDELINES.md
 * ("UI Design Reference"). All the add/manage affordances are gated on
 * `AgeGroupDetails.canManage` (COORDINATOR+ or a coach of this age group).
 */
@Composable
fun AgeGroupDetailsScreen(
    ageGroupId: String,
    onNavigateBack: () -> Unit,
    onNavigateToTeam: (teamId: String) -> Unit,
    onNavigateToPlayer: (playerId: String) -> Unit,
    onNavigateToAddPlayers: (clubId: String, ageGroupId: String) -> Unit,
    onNavigateToCreateTeams: (clubId: String, clubName: String, sport: SportType) -> Unit,
    onNavigateToCreateMatch: (ageGroupId: String) -> Unit,
    refreshOnReturn: Boolean = false,
    viewModel: AgeGroupDetailsViewModel = koinViewModel()
) {
    val uiState by viewModel.state.collectAsState()
    var playerSort by remember { mutableStateOf(PlayerSort.NAME) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(ageGroupId) { viewModel.load(ageGroupId) }
    LaunchedEffect(refreshOnReturn) { if (refreshOnReturn) viewModel.load(ageGroupId, forceReload = true) }

    AgeGroupDetailsContent(
        uiState = uiState,
        playerSort = playerSort,
        searchQuery = searchQuery,
        onNavigateBack = onNavigateBack,
        onRetry = { viewModel.load(ageGroupId, forceReload = true) },
        onPlayerSortChange = { playerSort = it },
        onSearchChange = { searchQuery = it },
        onPlayerClick = onNavigateToPlayer,
        onTeamClick = onNavigateToTeam,
        onAddPlayers = { d -> onNavigateToAddPlayers(d.ageGroup.clubId, d.ageGroup.id) },
        onCreateTeam = { d -> onNavigateToCreateTeams(d.ageGroup.clubId, d.club.name, d.club.sport) },
        onCreateMatch = { d -> onNavigateToCreateMatch(d.ageGroup.id) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgeGroupDetailsContent(
    uiState: AgeGroupDetailsUiState,
    playerSort: PlayerSort,
    searchQuery: String,
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit,
    onPlayerSortChange: (PlayerSort) -> Unit,
    onSearchChange: (String) -> Unit,
    onPlayerClick: (String) -> Unit,
    onTeamClick: (String) -> Unit,
    onAddPlayers: (AgeGroupDetails) -> Unit,
    onCreateTeam: (AgeGroupDetails) -> Unit,
    onCreateMatch: (AgeGroupDetails) -> Unit
) {
    val details = (uiState as? AgeGroupDetailsUiState.Idle)?.details
    Scaffold(
        containerColor = Color.White,
        topBar = {
            FMTopBar(
                title = details?.ageGroup?.name ?: stringResource(Res.string.tab_age_groups),
                subtitle = details?.let { d ->
                    "${d.season.name} · ${d.season.startDate.format(MonthYear)} – ${d.season.endDate.format(MonthYear)}"
                },
                onNavigateBack = onNavigateBack,
                actions = {
                    if (details != null && details.canManage) {
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
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.label_add_players_to_age_group), color = DeepCarbon) },
                                    onClick = { open = false; onAddPlayers(details) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.label_create_team), color = DeepCarbon) },
                                    onClick = { open = false; onCreateTeam(details) }
                                )
                                if (details.teams.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(Res.string.label_schedule_match), color = DeepCarbon) },
                                        onClick = { open = false; onCreateMatch(details) }
                                    )
                                }
                            }
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        val boxModifier = Modifier.fillMaxSize().padding(paddingValues).background(Color.White)
        when (uiState) {
            is AgeGroupDetailsUiState.Loading ->
                Box(boxModifier, contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PitchGreen) }

            is AgeGroupDetailsUiState.Error ->
                Column(boxModifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(uiState.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRetry) { Text(stringResource(Res.string.btn_retry)) }
                }

            is AgeGroupDetailsUiState.Idle -> Column(Modifier.fillMaxSize().padding(paddingValues)) {
                // The counts + eligibility rule sit in a dark block flush with the top bar (same
                // CarbonGray), edge to edge; the white scrolling content starts below it.
                AgeGroupHeader(
                    birthYear = uiState.details.ageGroup.birthYear,
                    players = uiState.details.players.size,
                    staff = uiState.details.staff.size,
                    teams = uiState.details.teams.size,
                    upcoming = uiState.details.upcomingMatches.size
                )
                AgeGroupBody(
                    details = uiState.details,
                    playerSort = playerSort,
                    searchQuery = searchQuery,
                    modifier = Modifier.fillMaxWidth().weight(1f).background(Color.White),
                    onPlayerSortChange = onPlayerSortChange,
                    onSearchChange = onSearchChange,
                    onPlayerClick = onPlayerClick,
                    onTeamClick = onTeamClick,
                    onAddPlayers = { onAddPlayers(uiState.details) },
                    onCreateTeam = { onCreateTeam(uiState.details) },
                    onCreateMatch = { onCreateMatch(uiState.details) }
                )
            }
        }
    }
}

@Composable
private fun AgeGroupBody(
    details: AgeGroupDetails,
    playerSort: PlayerSort,
    searchQuery: String,
    modifier: Modifier,
    onPlayerSortChange: (PlayerSort) -> Unit,
    onSearchChange: (String) -> Unit,
    onPlayerClick: (String) -> Unit,
    onTeamClick: (String) -> Unit,
    onAddPlayers: () -> Unit,
    onCreateTeam: () -> Unit,
    onCreateMatch: () -> Unit
) {
    val ag = details.ageGroup
    val opponentsById = remember(details.opponents) { details.opponents.associateBy { it.id } }
    val teamsById = remember(details.teams) { details.teams.associateBy { it.id } }

    val visiblePlayers = remember(details.players, playerSort, searchQuery) {
        val q = searchQuery.trim().lowercase()
        details.players
            .asSequence()
            .filter { q.isEmpty() || "${it.firstName} ${it.lastName}".lowercase().contains(q) }
            .sortedWith(
                when (playerSort) {
                    PlayerSort.NAME -> compareBy({ it.lastName.lowercase() }, { it.firstName.lowercase() })
                    PlayerSort.BIRTH_YEAR -> compareByDescending<Player> { it.birthDate.year }
                        .thenBy { it.lastName.lowercase() }
                }
            )
            .toList()
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ---- Players ----
        item {
            SectionHeader(
                title = stringResource(Res.string.tab_players),
                count = details.players.size,
                onAdd = if (details.canManage) onAddPlayers else null
            )
        }
        if (details.players.isEmpty()) {
            item {
                EmptySectionCard(
                    title = stringResource(Res.string.msg_no_players_in_age_group_title),
                    hint = stringResource(Res.string.msg_no_players_in_age_group_hint),
                    action = if (details.canManage) stringResource(Res.string.btn_add_players) to onAddPlayers else null
                )
            }
        } else {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.label_sort), style = MaterialTheme.typography.bodySmall, color = DeepCarbon.copy(alpha = 0.5f))
                    SortChip(stringResource(Res.string.label_sort_name), playerSort == PlayerSort.NAME) { onPlayerSortChange(PlayerSort.NAME) }
                    SortChip(stringResource(Res.string.label_sort_birth_year), playerSort == PlayerSort.BIRTH_YEAR) { onPlayerSortChange(PlayerSort.BIRTH_YEAR) }
                }
            }
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    label = { Text(stringResource(Res.string.label_search_players)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DeepCarbon,
                        unfocusedTextColor = DeepCarbon,
                        focusedBorderColor = PitchGreen,
                        unfocusedBorderColor = CarbonGray.copy(alpha = 0.3f),
                        focusedLabelColor = PitchGreen,
                        unfocusedLabelColor = CarbonGray
                    )
                )
            }
            items(visiblePlayers, key = { it.id }) { player ->
                PlayerRow(
                    player = player,
                    playsUp = player.birthDate.year > ag.birthYear,
                    onClick = { onPlayerClick(player.id) }
                )
            }
        }

        // ---- Staff ----
        item { SectionHeader(title = stringResource(Res.string.label_age_group_staff), count = details.staff.size, onAdd = null) }
        if (details.staff.isEmpty()) {
            item {
                EmptySectionCard(
                    title = stringResource(Res.string.msg_no_staff_age_group),
                    hint = null,
                    action = null
                )
            }
        } else {
            items(details.staff, key = { it.userId }) { member -> StaffRow(member) }
        }

        // ---- Teams ----
        item {
            SectionHeader(
                title = stringResource(Res.string.tab_teams),
                count = details.teams.size,
                onAdd = if (details.canManage) onCreateTeam else null
            )
        }
        if (details.teams.isEmpty()) {
            item {
                EmptySectionCard(
                    title = stringResource(Res.string.msg_no_teams_in_age_group),
                    hint = stringResource(Res.string.msg_no_teams_in_age_group_hint),
                    action = if (details.canManage) stringResource(Res.string.label_create_team) to onCreateTeam else null
                )
            }
        } else {
            items(details.teams, key = { it.id }) { team ->
                TeamRow(team = team, onClick = { onTeamClick(team.id) })
            }
        }

        // ---- Upcoming matches ----
        // A match belongs to a team, so scheduling is only offered once the age group has one.
        val canSchedule = details.canManage && details.teams.isNotEmpty()
        item {
            SectionHeader(
                title = stringResource(Res.string.label_upcoming_matches),
                count = details.upcomingMatches.size,
                onAdd = if (canSchedule) onCreateMatch else null
            )
        }
        if (details.upcomingMatches.isEmpty()) {
            item {
                EmptySectionCard(
                    title = stringResource(Res.string.msg_no_upcoming_matches),
                    hint = null,
                    action = if (canSchedule) stringResource(Res.string.label_schedule_match) to onCreateMatch else null
                )
            }
        } else {
            items(details.upcomingMatches, key = { it.id }) { match ->
                MatchRow(
                    opponentName = opponentsById[match.opponentId]?.let { it.shortName ?: it.name } ?: "—",
                    teamName = teamsById[match.teamId]?.name ?: "",
                    dateLabel = matchDateLabel(match.dateTime)
                )
            }
        }
    }
}

private fun matchDateLabel(instant: Instant): String {
    val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val time = "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
    return "${dt.date.format(DayMonth)} · $time"
}

/** The dark block directly under the top bar (same CarbonGray): eligibility rule + edge-to-edge counts. */
@Composable
private fun AgeGroupHeader(birthYear: Int, players: Int, staff: Int, teams: Int, upcoming: Int) {
    Column(modifier = Modifier.fillMaxWidth().background(CarbonGray)) {
        EligibilityPill(
            text = stringResource(Res.string.label_eligibility_rule, birthYear),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)
        )
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.10f)))
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
        ) {
            Stat(players.toString(), stringResource(Res.string.tab_players), Modifier.weight(1f))
            HeaderDivider()
            Stat(staff.toString(), stringResource(Res.string.label_age_group_staff), Modifier.weight(1f))
            HeaderDivider()
            Stat(teams.toString(), stringResource(Res.string.tab_teams), Modifier.weight(1f))
            HeaderDivider()
            Stat(upcoming.toString(), stringResource(Res.string.label_upcoming), Modifier.weight(1f), highlight = upcoming > 0)
        }
    }
}

@Composable
private fun EligibilityPill(text: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(10.dp), color = Color.White.copy(alpha = 0.08f), modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(16.dp).background(PitchGreen, CircleShape), contentAlignment = Alignment.Center) {
                Text("i", color = Color.White, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
            }
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier, highlight: Boolean = false) {
    Column(modifier = modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = if (highlight) PitchGreen else Color.White
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
    }
}

@Composable
private fun HeaderDivider() {
    Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.10f)))
}

@Composable
private fun SectionHeader(title: String, count: Int, onAdd: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
        Spacer(Modifier.width(6.dp))
        Text(count.toString(), style = MaterialTheme.typography.bodyMedium, color = DeepCarbon.copy(alpha = 0.4f))
        Spacer(Modifier.weight(1f))
        if (onAdd != null) {
            Text(
                "+ ${stringResource(Res.string.label_add)}",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = PitchGreen,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onAdd
                )
            )
        }
    }
}

@Composable
private fun SortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) DeepCarbon else CarbonGray.copy(alpha = 0.08f),
        border = if (selected) null else BorderStroke(1.dp, CarbonGray.copy(alpha = 0.2f))
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = if (selected) Color.White else DeepCarbon
        )
    }
}

@Composable
private fun PlayerRow(player: Player, playsUp: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        color = Color.White,
        border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.15f))
    ) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            InitialsAvatar("${player.firstName.take(1)}${player.lastName.take(1)}")
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${player.firstName} ${player.lastName}",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = if (player.active) DeepCarbon else DeepCarbon.copy(alpha = 0.4f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(player.birthDate.year.toString(), style = MaterialTheme.typography.bodySmall, color = DeepCarbon.copy(alpha = 0.55f))
                    if (playsUp) {
                        Spacer(Modifier.width(8.dp))
                        MiniChip(stringResource(Res.string.label_plays_up))
                    }
                }
            }
            player.shirtNumber?.let {
                Text(it.toString(), style = MaterialTheme.typography.bodyMedium, color = DeepCarbon.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun StaffRow(member: AgeGroupStaffMember) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        color = Color.White,
        border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.15f))
    ) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            InitialsAvatar("${member.firstName.take(1)}${member.lastName.take(1)}")
            Spacer(Modifier.width(12.dp))
            Text(
                "${member.firstName} ${member.lastName}",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = DeepCarbon, modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            MiniChip(
                stringResource(if (member.role == TeamRole.COACH) Res.string.label_role_coach else Res.string.label_role_staff)
            )
        }
    }
}

@Composable
private fun TeamRow(team: Team, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        color = Color.White,
        border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.15f))
    ) {
        Row(modifier = Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(36.dp).background(PitchGreen, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) { Text(team.name.take(1), color = Color.White, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(12.dp))
            Text(
                team.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = if (team.active) DeepCarbon else DeepCarbon.copy(alpha = 0.4f),
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            team.code?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = DeepCarbon.copy(alpha = 0.5f))
            }
            Text(" ›", color = DeepCarbon.copy(alpha = 0.4f), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MatchRow(opponentName: String, teamName: String, dateLabel: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        color = Color.White,
        border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.15f))
    ) {
        Row(modifier = Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("vs $opponentName", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = DeepCarbon, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (teamName.isNotEmpty()) {
                    Text(teamName, style = MaterialTheme.typography.bodySmall, color = DeepCarbon.copy(alpha = 0.55f))
                }
            }
            Text(dateLabel, style = MaterialTheme.typography.bodySmall, color = DeepCarbon.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun InitialsAvatar(initials: String) {
    Box(
        modifier = Modifier.size(40.dp).background(CarbonGray.copy(alpha = 0.1f), CircleShape),
        contentAlignment = Alignment.Center
    ) { Text(initials, color = DeepCarbon, fontWeight = FontWeight.Bold) }
}

@Composable
private fun MiniChip(text: String) {
    Surface(shape = RoundedCornerShape(6.dp), color = PitchGreen.copy(alpha = 0.15f)) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = PitchGreen
        )
    }
}

@Composable
private fun EmptySectionCard(title: String, hint: String?, action: Pair<String, () -> Unit>?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        color = Color.White,
        border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = DeepCarbon, textAlign = TextAlign.Center)
            if (hint != null) {
                Spacer(Modifier.height(4.dp))
                Text(hint, style = MaterialTheme.typography.bodySmall, color = DeepCarbon.copy(alpha = 0.6f), textAlign = TextAlign.Center)
            }
            if (action != null) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = action.second,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PitchGreen, contentColor = Color.White)
                ) { Text(action.first) }
            }
        }
    }
}

// ---- Previews ----

private fun sampleDetails(populated: Boolean, canManage: Boolean): AgeGroupDetails {
    val ag = AgeGroup(id = "ag1", clubId = "c1", name = "Sub 2015", seasonId = "s1", birthYear = 2015)
    val club = Club(id = "c1", name = "CD Ribamar", status = ClubStatus.ACTIVE, sport = SportType.FOOTBALL, createdAt = Instant.fromEpochMilliseconds(0))
    val season = Season(id = "s1", clubId = "c1", name = "26/27", type = SeasonType.EUROPEAN_YEAR, startDate = LocalDate(2026, 7, 1), endDate = LocalDate(2027, 6, 30), active = true)
    fun p(id: String, f: String, l: String, y: Int, n: Int?) = Player(id, "c1", f, l, LocalDate(y, 3, 1), n, null, true)
    return AgeGroupDetails(
        ageGroup = ag, club = club, season = season,
        players = if (populated) listOf(
            p("p1", "Afonso", "Rebelo", 2015, 7),
            p("p2", "Bruno", "Tavares", 2015, 4),
            p("p3", "Caetano", "Lousada", 2016, 11)
        ) else emptyList(),
        staff = if (populated) listOf(
            AgeGroupStaffMember("u1", "Nuno", "Faria", TeamRole.COACH),
            AgeGroupStaffMember("u2", "Rita", "Sousa", TeamRole.STAFF)
        ) else emptyList(),
        teams = if (populated) listOf(
            Team("t1", "c1", "ag1", "s1", "Sub 2015 A", "A", true),
            Team("t2", "c1", "ag1", "s1", "Sub 2015 B", "B", true)
        ) else emptyList(),
        upcomingMatches = emptyList(),
        opponents = emptyList(),
        canManage = canManage
    )
}

@Preview
@Composable
private fun AgeGroupDetailsPopulatedPreview() {
    ClubManagerTheme {
        AgeGroupDetailsContent(
            uiState = AgeGroupDetailsUiState.Idle(sampleDetails(populated = true, canManage = true)),
            playerSort = PlayerSort.NAME, searchQuery = "",
            onNavigateBack = {}, onRetry = {}, onPlayerSortChange = {}, onSearchChange = {},
            onPlayerClick = {}, onTeamClick = {}, onAddPlayers = {}, onCreateTeam = {}, onCreateMatch = {}
        )
    }
}

@Preview
@Composable
private fun AgeGroupDetailsEmptyReadOnlyPreview() {
    ClubManagerTheme(darkTheme = true) {
        AgeGroupDetailsContent(
            uiState = AgeGroupDetailsUiState.Idle(sampleDetails(populated = false, canManage = false)),
            playerSort = PlayerSort.NAME, searchQuery = "",
            onNavigateBack = {}, onRetry = {}, onPlayerSortChange = {}, onSearchChange = {},
            onPlayerClick = {}, onTeamClick = {}, onAddPlayers = {}, onCreateTeam = {}, onCreateMatch = {}
        )
    }
}

@Preview(widthDp = 780, heightDp = 360)
@Composable
private fun AgeGroupDetailsLandscapePreview() {
    ClubManagerTheme {
        AgeGroupDetailsContent(
            uiState = AgeGroupDetailsUiState.Idle(sampleDetails(populated = true, canManage = true)),
            playerSort = PlayerSort.BIRTH_YEAR, searchQuery = "",
            onNavigateBack = {}, onRetry = {}, onPlayerSortChange = {}, onSearchChange = {},
            onPlayerClick = {}, onTeamClick = {}, onAddPlayers = {}, onCreateTeam = {}, onCreateMatch = {}
        )
    }
}
