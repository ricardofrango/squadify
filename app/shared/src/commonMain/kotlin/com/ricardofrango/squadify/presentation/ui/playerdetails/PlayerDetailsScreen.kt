package com.ricardofrango.squadify.presentation.ui.playerdetails

import com.ricardofrango.squadify.presentation.ui.components.PersonPhoto
import com.ricardofrango.squadify.presentation.ui.theme.OnChromeAccent
import com.ricardofrango.squadify.presentation.ui.theme.ClubChrome
import com.ricardofrango.squadify.presentation.ui.theme.ChromeAccent
import com.ricardofrango.squadify.model.PlayerTrainingSeason
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyClubTheme
import com.ricardofrango.squadify.presentation.ui.training.PlayerTrainingCard
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
import com.ricardofrango.squadify.presentation.ui.theme.WhiteSmoke
import com.ricardofrango.squadify.presentation.ui.components.FMTopBar
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
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
 * Reference"). One dark header (avatar, name, shirt number/position/age-group line) over the body:
 * the Season stats card and its per-match list (design 30), then the Player Information card. The
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
            onOpenMatch = onOpenMatch
        )
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
    onOpenMatch: (String) -> Unit
) {
    val profile = (uiState as? PlayerDetailsUiState.Idle)?.profile

    Scaffold(
        containerColor = WhiteSmoke,
        topBar = {
            Column(Modifier.background(ClubChrome)) {
                FMTopBar(
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
                if (profile != null) PlayerHeaderBand(profile)
            }
        }
    ) { paddingValues ->
        val boxModifier = Modifier.fillMaxSize().padding(paddingValues).background(WhiteSmoke)
        when (uiState) {
            is PlayerDetailsUiState.Loading -> {
                Box(boxModifier, contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PitchGreen)
                }
            }
            is PlayerDetailsUiState.Error ->
                Column(boxModifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(uiState.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRetry) { Text(stringResource(Res.string.btn_retry)) }
                }
            is PlayerDetailsUiState.Idle -> PlayerBody(
                profile = uiState.profile,
                statsState = statsState,
                training = training,
                modifier = boxModifier,
                onRetryStats = onRetryStats,
                onSeasonSelected = onSeasonSelected,
                onFilterSelected = onFilterSelected,
                onOpenMatch = onOpenMatch
            )
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
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
    modifier: Modifier = Modifier,
    onRetryStats: () -> Unit,
    onSeasonSelected: (String) -> Unit,
    onFilterSelected: (StatsFilter) -> Unit,
    onOpenMatch: (String) -> Unit
) {
    var showSeasons by remember { mutableStateOf(false) }
    val loaded = statsState as? SeasonStatsUiState.Loaded
    val matches = loaded?.visibleMatches().orEmpty()
    val showRatings = loaded?.stats?.canSeeRatings == true

    val statsCard: @Composable () -> Unit = { Column {
        PlayerSeasonStatsCard(
            state = statsState,
            playerFirstName = profile.player.firstName,
            onRetry = onRetryStats,
            onOpenSeasons = { showSeasons = true },
            onFilterSelected = onFilterSelected
        )
        // Staff only (design 39j): the season shown above, else the latest one with training.
        (training.firstOrNull { it.seasonId == loaded?.selectedSeasonId } ?: training.firstOrNull())?.let {
            Spacer(Modifier.height(12.dp))
            PlayerTrainingCard(it)
        }
    } }
    val padding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp)

    // Landscape (30e): totals and Player info on the left, the match list beside them.
    BoxWithConstraints(modifier) {
        if (maxWidth > maxHeight && maxWidth >= 600.dp) {
            Row(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.weight(1f), contentPadding = padding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { statsCard() }
                    item { PlayerInfoCard(profile) }
                }
                LazyColumn(Modifier.weight(1f), contentPadding = padding.copyStart(0.dp)) {
                    if (matches.isNotEmpty()) item { PlayerMatchesList(matches, showRatings, onOpenMatch) }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = padding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { statsCard() }
                if (matches.isNotEmpty()) item { PlayerMatchesList(matches, showRatings, onOpenMatch) }
                item { PlayerInfoCard(profile) }
            }
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

private fun PaddingValues.copyStart(start: androidx.compose.ui.unit.Dp) = PaddingValues(
    start = start,
    end = calculateEndPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
    top = calculateTopPadding(),
    bottom = calculateBottomPadding()
)

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
private fun PlayerDetailsLoadingPreview() = PreviewContent(PlayerDetailsUiState.Loading, SeasonStatsUiState.Loading)

@Preview
@Composable
private fun PlayerDetailsErrorPreview() =
    PreviewContent(PlayerDetailsUiState.Error("Couldn't load this player. Please try again."), SeasonStatsUiState.Loading)
