package com.ricardofrango.squadify.presentation.ui.playerdetails

import com.ricardofrango.squadify.presentation.ui.components.ScreenWindowInsets
import com.ricardofrango.squadify.presentation.ui.components.ErrorScreen
import com.ricardofrango.squadify.presentation.ui.components.ShownSpace
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import org.jetbrains.compose.resources.getString
import squadify.app.shared.generated.resources.msg_refresh_failed
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.ricardofrango.squadify.presentation.ui.components.PersonPhoto
import com.ricardofrango.squadify.presentation.ui.theme.OnChromeAccent
import com.ricardofrango.squadify.presentation.ui.theme.ClubChrome
import com.ricardofrango.squadify.presentation.ui.theme.ChromeAccent
import com.ricardofrango.squadify.model.PlayerTrainingSeason
import com.ricardofrango.squadify.presentation.ui.theme.ScreenPadding
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyClubTheme
import com.ricardofrango.squadify.presentation.navigation.collectSettledState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricardofrango.squadify.domain.AgeGroupDefaults
import com.ricardofrango.squadify.model.MatchType
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.PlayerPosition
import com.ricardofrango.squadify.model.PlayerProfile
import com.ricardofrango.squadify.model.PlayerSeason
import com.ricardofrango.squadify.model.PlayerSeasonMatch
import com.ricardofrango.squadify.model.PlayerSeasonStats
import com.ricardofrango.squadify.model.StatsFilter
import com.ricardofrango.squadify.presentation.ui.theme.ScreenBackground
import com.ricardofrango.squadify.presentation.ui.components.SquadifyTopBar
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.screenContentPadding
import com.ricardofrango.squadify.presentation.ui.components.labelRes
import squadify.app.shared.generated.resources.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock
import kotlin.time.Instant

private val CardShape = RoundedCornerShape(16.dp)

private val DayMonthYear = LocalDate.Format { day(); char(' '); monthName(MonthNames.ENGLISH_ABBREVIATED); char(' '); year() }

/**
 * The player profile screen (design canvas artboard "2C", see AI_GUIDELINES.md "UI Design
 * Reference"). One dark header (avatar, name, shirt number/position/age-group line) and its tabs over
 * the body: General (a season summary, then Player Information), Games (the Season stats card over the season's games and
 * their ratings) and, for staff in the clubs app, Training (the training card over every ended session). The
 * stats load separately, so their loading/error states stay inside the card. The overflow menu's
 * "Edit player" (design "17") is gated on [PlayerProfile.canEditPlayer].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerDetailsScreen(
    playerId: String,
    onNavigateBack: () -> Unit,
    onEditPlayer: (String) -> Unit,
    onOpenMatch: (String) -> Unit,
    initialSeasonId: String? = null,
    initialFilter: StatsFilter? = null,
    refreshOnReturn: Boolean = false,
    viewModel: PlayerDetailsViewModel = koinViewModel()
) {
    SquadifyClubTheme {
        val uiState = viewModel.state.collectSettledState()
        val statsState by viewModel.stats.collectAsStateWithLifecycle()
        val training by viewModel.training.collectAsStateWithLifecycle()

        LaunchedEffect(playerId) {
            viewModel.loadPlayerDetails(playerId, initialSeasonId, initialFilter)
        }
        LaunchedEffect(refreshOnReturn) {
            if (refreshOnReturn) viewModel.loadPlayerDetails(playerId)
        }
        val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
        val snackbarHostState = remember { SnackbarHostState() }
        val refreshFailedText = Res.string.msg_refresh_failed
        LaunchedEffect(viewModel) {
            viewModel.refreshFailed.collect { snackbarHostState.showSnackbar(getString(refreshFailedText)) }
        }

        // The plan boxes in it: See plans only in the coach's own space.
        ShownSpace((uiState as? PlayerDetailsUiState.Idle)?.profile?.player?.clubId) {
            PlayerDetailsContent(
                uiState = uiState,
                statsState = statsState,
                training = training,
                onNavigateBack = onNavigateBack,
                onRetry = { viewModel.loadPlayerDetails(playerId) },
                onRetryStats = { viewModel.loadSeasonStats(playerId) },
                onEditPlayer = { onEditPlayer(playerId) },
                onSeasonSelected = viewModel::onSeasonSelected,
                onFilterSelected = viewModel::onFilterSelected,
                onOpenMatch = onOpenMatch,
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refresh(playerId) },
                snackbarHostState = snackbarHostState
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerDetailsContent(
    uiState: PlayerDetailsUiState,
    statsState: SeasonStatsUiState,
    training: List<PlayerTrainingSeason> = emptyList(),
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit,
    onRetryStats: () -> Unit,
    onEditPlayer: () -> Unit,
    onSeasonSelected: (String) -> Unit,
    onFilterSelected: (StatsFilter) -> Unit,
    onOpenMatch: (String) -> Unit,
    // Pull-to-refresh: the content stays under the small indicator.
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val profile = (uiState as? PlayerDetailsUiState.Idle)?.profile
    val tabs = playerTabs(training)
    var tab by rememberSaveable { mutableStateOf(PlayerTab.GENERAL) }
    // Training can go (a refresh that no longer returns it): fall back to General.
    val selectedTab = tab.takeIf { it in tabs } ?: PlayerTab.GENERAL

    Scaffold(
        contentWindowInsets = ScreenWindowInsets,
        containerColor = ScreenBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(Modifier.background(ClubChrome)) {
                SquadifyTopBar(
                    title = stringResource(Res.string.title_player_profile),
                    onNavigateBack = onNavigateBack,
                    actions = {
                        if (profile != null && profile.canEditPlayer) {
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
                                        text = { Text(stringResource(Res.string.label_edit_player), color = DeepCarbon) },
                                        onClick = { open = false; onEditPlayer() }
                                    )
                                }
                            }
                        }
                    }
                )
                if (profile != null) {
                    PlayerHeaderBand(profile)
                    PlayerTabRow(tabs, selectedTab) { tab = it }
                }
            }
        }
    ) { paddingValues ->
        val boxModifier = Modifier.fillMaxSize().padding(paddingValues).background(ScreenBackground)
        when (uiState) {
            is PlayerDetailsUiState.Loading -> {
                Box(boxModifier, contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PitchGreen)
                }
            }
            is PlayerDetailsUiState.Error ->
                ErrorScreen(title = uiState.message, body = null, onRetry = onRetry, modifier = boxModifier)
            is PlayerDetailsUiState.Idle -> PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = boxModifier) {
                PlayerBody(
                    profile = uiState.profile,
                    statsState = statsState,
                    training = training,
                    tab = selectedTab,
                    modifier = Modifier.fillMaxSize(),
                    onRetryStats = onRetryStats,
                    onSeasonSelected = onSeasonSelected,
                    onFilterSelected = onFilterSelected,
                    onOpenMatch = onOpenMatch
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Header (dark)
// ---------------------------------------------------------------------------------------------

@Composable
private fun PlayerHeaderBand(profile: PlayerProfile) {
    val player = profile.player
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding.Horizontal, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PersonPhoto(
            photoUrl = player.photoUrl,
            initials = "${player.firstName.take(1)}${player.lastName.take(1)}",
            size = 64.dp,
            background = ChromeAccent.copy(alpha = 0.25f),
            initialsColor = ChromeAccent,
            initialsStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                "${player.firstName} ${player.lastName}",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                player.shirtNumber?.let { number ->
                    Surface(shape = RoundedCornerShape(6.dp), color = ChromeAccent) {
                        Text(
                            number.toString(),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = OnChromeAccent
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    subtitleLine(profile),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.75f)
                )
            }
        }
    }
}

/** "Midfielder · Sub 11" (position omitted if not set). */
@Composable
private fun subtitleLine(profile: PlayerProfile): String {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val subLabel = AgeGroupDefaults.inferredName(
        birthYear = profile.player.birthDate.year,
        seasonEndYear = AgeGroupDefaults.currentSeasonEndYear(today)
    )
    val positionText = profile.player.position?.let { stringResource(it.labelRes()) }
    return listOfNotNull(positionText, subLabel).joinToString(" · ")
}


// ---------------------------------------------------------------------------------------------
// Body
// ---------------------------------------------------------------------------------------------

@Composable
private fun PlayerBody(
    profile: PlayerProfile,
    statsState: SeasonStatsUiState,
    training: List<PlayerTrainingSeason> = emptyList(),
    tab: PlayerTab = PlayerTab.GENERAL,
    modifier: Modifier = Modifier,
    onRetryStats: () -> Unit,
    onSeasonSelected: (String) -> Unit,
    onFilterSelected: (StatsFilter) -> Unit,
    onOpenMatch: (String) -> Unit
) {
    var showSeasons by remember { mutableStateOf(false) }
    val loaded = statsState as? SeasonStatsUiState.Loaded
    val trainingSeason = training.forSeason(loaded?.selectedSeasonId)

    LazyColumn(modifier, contentPadding = ScreenPadding.Values, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (tab) {
            PlayerTab.GENERAL -> {
                // The same season's training only: the overall rating mixes the two.
                if (loaded?.selectedSeason != null) item {
                    PlayerSummaryCard(loaded, training.firstOrNull { it.seasonId == loaded.selectedSeasonId }) { showSeasons = true }
                }
                item { PlayerInfoCard(profile) }
            }
            PlayerTab.GAMES -> item {
                PlayerGamesTab(
                    state = statsState,
                    playerFirstName = profile.player.firstName,
                    onRetry = onRetryStats,
                    onOpenSeasons = { showSeasons = true },
                    onFilterSelected = onFilterSelected,
                    onOpenMatch = onOpenMatch
                )
            }
            PlayerTab.TRAINING -> trainingSeason?.let { item { PlayerTrainingTab(it) } }
        }
    }

    if (showSeasons && loaded != null) {
        SeasonSheet(
            seasons = loaded.stats.seasons,
            selectedSeasonId = loaded.selectedSeasonId,
            onSelect = { onSeasonSelected(it); showSeasons = false },
            onDismiss = { showSeasons = false }
        )
    }
}

@Composable
private fun PlayerInfoCard(profile: PlayerProfile) {
    val player = profile.player
    Surface(shape = CardShape, color = Color.White, border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.12f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(
                stringResource(Res.string.label_player_info),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = DeepCarbon
            )
            Spacer(Modifier.height(12.dp))
            InfoRow(
                stringResource(Res.string.label_status),
                stringResource(if (player.active) Res.string.label_active else Res.string.label_inactive)
            )
            InfoRow(stringResource(Res.string.label_birth_date), player.birthDate.format(DayMonthYear))
            InfoRow(stringResource(Res.string.label_age_group_field), profile.ageGroupLabel ?: "—")
            player.heightCm?.let { InfoRow(stringResource(Res.string.label_height), stringResource(Res.string.label_height_value, it)) }
            player.weightKg?.let { InfoRow(stringResource(Res.string.label_weight), stringResource(Res.string.label_weight_value, it)) }
            InfoRow(stringResource(Res.string.label_guardians), stringResource(Res.string.label_guardians_linked, profile.guardiansLinked))
            player.emergencyContact?.takeIf { it.isNotBlank() }?.let {
                InfoRow(stringResource(Res.string.label_emergency_contact), it)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = DeepCarbon.copy(alpha = 0.5f))
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
    }
}

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

private fun sampleProfile(withPosition: Boolean, canManage: Boolean = true): PlayerProfile = PlayerProfile(
    player = Player(
        id = "p1", clubId = "c1", firstName = "Tomás", lastName = "Bragança",
        birthDate = LocalDate(2011, 3, 12), shirtNumber = 10, active = true,
        position = if (withPosition) PlayerPosition.MIDFIELD else null,
        heightCm = if (withPosition) 158 else null,
        weightKg = if (withPosition) 47 else null,
        emergencyContact = if (withPosition) "Ana Bragança · +351 912 345 678" else null
    ),
    ageGroupId = "ag1",
    ageGroupLabel = "Sub-15 A",
    seasonLabel = "26/27",
    goals = 9,
    assists = 6,
    timesCalledUp = 14,
    guardiansLinked = 2,
    canEditPlayer = canManage,
    recentEvents = emptyList()
)

private fun sampleMatch(id: String, day: Int, type: MatchType?, started: Boolean, minutes: Int, goals: Int = 0, assists: Int = 0, yellow: Int = 0, rating: Int? = null) =
    PlayerSeasonMatch(
        matchId = id, dateTime = Instant.fromEpochMilliseconds(1_790_000_000_000L - day * 86_400_000L), opponentName = "AD Fontelas",
        matchType = type, started = started, minutesPlayed = minutes, goals = goals, assists = assists,
        ownGoals = 0, yellowCards = yellow, redCards = 0, rating = rating, teamName = "Sub-15 A"
    )

private fun sampleSeasonStats(staff: Boolean) = SeasonStatsUiState.Loaded(
    stats = PlayerSeasonStats(
        playerId = "p1",
        currentSeasonId = "s2",
        canSeeRatings = staff,
        seasons = listOf(
            PlayerSeason(
                "s2", "2026/27", listOf("Sub-15 A"),
                listOf(
                    sampleMatch("m1", 0, MatchType.CHAMPIONSHIP, true, 70, goals = 2, rating = if (staff) 9 else null),
                    sampleMatch("m2", 7, MatchType.CHAMPIONSHIP, true, 70, assists = 1, yellow = 1, rating = if (staff) 7 else null),
                    sampleMatch("m3", 13, MatchType.TOURNAMENT, true, 40, goals = 1, rating = if (staff) 8 else null),
                    sampleMatch("m4", 13, MatchType.TOURNAMENT, false, 20),
                    sampleMatch("m5", 28, MatchType.FRIENDLY, false, 35, goals = 1, rating = if (staff) 4 else null)
                )
            ),
            PlayerSeason("s1", "2024/25", listOf("Sub-13 A"), emptyList(), preStatsMatchCount = 9)
        )
    ),
    selectedSeasonId = "s2"
)

@Composable
private fun PreviewContent(uiState: PlayerDetailsUiState, statsState: SeasonStatsUiState) {
    SquadifyTheme {
        PlayerDetailsContent(
            uiState = uiState, statsState = statsState, onNavigateBack = {}, onRetry = {}, onRetryStats = {},
            onEditPlayer = {}, onSeasonSelected = {}, onFilterSelected = {}, onOpenMatch = {}
        )
    }
}

@Preview
@Composable
private fun PlayerDetailsStaffPreview() =
    PreviewContent(PlayerDetailsUiState.Idle(sampleProfile(withPosition = true)), sampleSeasonStats(staff = true))

@Preview
@Composable
private fun PlayerDetailsParentPreview() =
    PreviewContent(PlayerDetailsUiState.Idle(sampleProfile(withPosition = true, canManage = false)), sampleSeasonStats(staff = false))

@Preview
@Composable
private fun PlayerDetailsFilterEmptyPreview() =
    PreviewContent(
        PlayerDetailsUiState.Idle(sampleProfile(withPosition = true)),
        sampleSeasonStats(staff = true).let { it.copy(stats = it.stats.copy(seasons = it.stats.seasons.map { s -> s.copy(matches = s.matches.filter { m -> m.matchType != MatchType.FRIENDLY }) })) }
            .copy(filter = StatsFilter.FRIENDLY)
    )

@Preview
@Composable
private fun PlayerDetailsNewPlayerPreview() =
    PreviewContent(
        PlayerDetailsUiState.Idle(sampleProfile(withPosition = false)),
        SeasonStatsUiState.Loaded(PlayerSeasonStats("p1", listOf(PlayerSeason("s2", "2026/27", emptyList(), emptyList())), "s2", canSeeRatings = true), "s2")
    )

@Preview
@Composable
private fun PlayerDetailsPreStatsSeasonPreview() =
    PreviewContent(PlayerDetailsUiState.Idle(sampleProfile(withPosition = true)), sampleSeasonStats(staff = true).copy(selectedSeasonId = "s1"))

@Preview
@Composable
private fun PlayerDetailsStatsErrorPreview() =
    PreviewContent(PlayerDetailsUiState.Idle(sampleProfile(withPosition = true)), SeasonStatsUiState.Error)

@Preview
@Composable
private fun PlayerDetailsStatsNotInPlanPreview() =
    PreviewContent(PlayerDetailsUiState.Idle(sampleProfile(withPosition = true)), SeasonStatsUiState.NotInPlan)

@Preview
@Composable
private fun PlayerDetailsLoadingPreview() = PreviewContent(PlayerDetailsUiState.Loading, SeasonStatsUiState.Loading)

@Preview
@Composable
private fun PlayerDetailsErrorPreview() =
    PreviewContent(PlayerDetailsUiState.Error("Couldn't load this player. Please try again."), SeasonStatsUiState.Loading)
