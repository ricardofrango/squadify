package com.ricardofrango.squadify.presentation.ui.clubdetails

import com.ricardofrango.squadify.presentation.ui.components.ErrorScreen
import com.ricardofrango.squadify.presentation.ui.theme.ScreenBackground
import kotlinx.datetime.LocalDate
import kotlin.time.Instant
import com.ricardofrango.squadify.domain.usecase.ClubDetails
import com.ricardofrango.squadify.model.ClubStatus
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.presentation.ui.theme.ScreenPadding
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.presentation.ui.theme.AlertRed
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyClubTheme
import org.jetbrains.compose.resources.getString
import squadify.app.shared.generated.resources.btn_cancel
import squadify.app.shared.generated.resources.btn_disable
import squadify.app.shared.generated.resources.msg_disable_player
import squadify.app.shared.generated.resources.msg_player_disabled
import squadify.app.shared.generated.resources.msg_player_reactivated
import squadify.app.shared.generated.resources.title_disable_player
import com.ricardofrango.squadify.presentation.navigation.collectSettledState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.model.SportType
import com.ricardofrango.squadify.presentation.ui.components.SquadifyTopBar
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.screenContentPadding
import com.ricardofrango.squadify.presentation.ui.components.readableWidth
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.btn_retry
import squadify.app.shared.generated.resources.label_all_ages
import squadify.app.shared.generated.resources.label_all_seasons
import squadify.app.shared.generated.resources.label_search_players
import squadify.app.shared.generated.resources.tab_players

/**
 * The club's players, as its own screen (opened from the More panel) rather than a tab under Club
 * details' bottom bar: season, age-group and search filters, then the list. The empty states offer the
 * next step -- create an age group, register players, or add them to an age group's pool.
 */
@Composable
fun ClubPlayersScreen(
    clubId: String,
    onNavigateBack: () -> Unit,
    onOpenPlayer: (playerId: String) -> Unit,
    onCreateAgeGroups: (clubName: String, sport: SportType, seasonId: String?) -> Unit,
    onCreatePlayers: (clubName: String) -> Unit,
    onAddPlayersToAgeGroup: (clubName: String) -> Unit,
    // True right after returning from a screen that added players: reload, keeping the filters.
    refresh: Boolean = false,
    viewModel: ClubPlayersViewModel = koinViewModel()
) {
    SquadifyClubTheme {
        val state = viewModel.state.collectSettledState()
        LaunchedEffect(clubId) { viewModel.load(clubId) }
        LaunchedEffect(refresh) { if (refresh) viewModel.load(clubId, force = true) }

        val snackbarHostState = remember { SnackbarHostState() }
        val disabledText = Res.string.msg_player_disabled
        LaunchedEffect(viewModel) {
            viewModel.disabled.collect { result ->
                snackbarHostState.showSnackbar(result.fold({ getString(disabledText, it) }, { it.message.orEmpty() }))
            }
        }
        val reactivatedText = Res.string.msg_player_reactivated
        LaunchedEffect(viewModel) {
            viewModel.reactivated.collect { result ->
                snackbarHostState.showSnackbar(result.fold({ getString(reactivatedText, it) }, { it.message.orEmpty() }))
            }
        }
        ClubPlayersContent(
            state = state,
            snackbarHostState = snackbarHostState,
            onNavigateBack = onNavigateBack,
            onOpenPlayer = onOpenPlayer,
            onCreateAgeGroups = onCreateAgeGroups,
            onCreatePlayers = onCreatePlayers,
            onAddPlayersToAgeGroup = onAddPlayersToAgeGroup,
            onRetry = { viewModel.load(clubId, force = true) },
            onSeasonSelected = viewModel::onSeasonSelected,
            onAgeGroupSelected = viewModel::onAgeGroupSelected,
            onSearchChanged = viewModel::onSearchChanged,
            onDisable = viewModel::disable,
            onReactivate = viewModel::reactivate,
            onLoadMore = viewModel::loadMore
        )
    }
}

@Composable
private fun Filters(
    state: ClubPlayersUiState.Idle,
    onSeasonSelected: (String?) -> Unit,
    onAgeGroupSelected: (String?) -> Unit,
    onSearchChanged: (String) -> Unit
) {
    val details = state.details
    // Lined up with the list under it, which is centred on a wide screen.
    Column(Modifier.readableWidth().padding(start = ScreenPadding.Horizontal, end = ScreenPadding.Horizontal, top = ScreenPadding.Top, bottom = 20.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            item {
                FilterSelector(
                    selectedOption = details.seasons.find { it.id == state.seasonId }?.name ?: stringResource(Res.string.label_all_seasons),
                    options = listOf(null to stringResource(Res.string.label_all_seasons)) + details.seasons.map { it.id to it.name },
                    onOptionSelected = onSeasonSelected
                )
            }
            item {
                FilterSelector(
                    selectedOption = details.ageGroups.find { it.id == state.ageGroupId }?.name ?: stringResource(Res.string.label_all_ages),
                    options = listOf(null to stringResource(Res.string.label_all_ages)) + state.availableAgeGroups.map { it.id to it.name },
                    onOptionSelected = onAgeGroupSelected
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.query,
            onValueChange = onSearchChanged,
            label = { Text(stringResource(Res.string.label_search_players)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White, disabledContainerColor = Color.White, 
                focusedTextColor = DeepCarbon, unfocusedTextColor = DeepCarbon,
                focusedBorderColor = PitchGreen, unfocusedBorderColor = CarbonGray.copy(alpha = 0.3f),
                focusedLabelColor = PitchGreen, unfocusedLabelColor = CarbonGray
            )
        )
    }
}

/** Club players' layout, without the ViewModel, so it can be previewed. */
@Composable
private fun ClubPlayersContent(
    state: ClubPlayersUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onOpenPlayer: (playerId: String) -> Unit,
    onCreateAgeGroups: (clubName: String, sport: SportType, seasonId: String?) -> Unit,
    onCreatePlayers: (clubName: String) -> Unit,
    onAddPlayersToAgeGroup: (clubName: String) -> Unit,
    onRetry: () -> Unit,
    onSeasonSelected: (String?) -> Unit,
    onAgeGroupSelected: (String?) -> Unit,
    onSearchChanged: (String) -> Unit,
    onDisable: (playerId: String) -> Unit,
    onReactivate: (playerId: String) -> Unit,
    onLoadMore: () -> Unit = {}
) {
    var confirmDisable by remember { mutableStateOf<Player?>(null) }
    Scaffold(
        containerColor = ScreenBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SquadifyTopBar(
                title = stringResource(Res.string.tab_players),
                subtitle = (state as? ClubPlayersUiState.Idle)?.details?.club?.name,
                onNavigateBack = onNavigateBack
            )
        }
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding).background(ScreenBackground)
        when (val s = state) {
            ClubPlayersUiState.Loading -> Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PitchGreen) }
            is ClubPlayersUiState.Error -> ErrorScreen(title = s.message, body = null, onRetry = onRetry, modifier = modifier)
            is ClubPlayersUiState.Idle -> Column(modifier) {
                val clubName = s.details.club.name
                Filters(s, onSeasonSelected, onAgeGroupSelected, onSearchChanged)
                when {
                    s.seasonId != null && s.availableAgeGroups.isEmpty() ->
                        NoAgeGroupsState(
                            canEditClub = s.details.canEditClub,
                            onCreateAgeGroupClick = { onCreateAgeGroups(clubName, s.details.club.sport, s.seasonId) }
                        )
                    !s.loaded -> Box(Modifier.fillMaxWidth().screenContentPadding(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = PitchGreen)
                    }
                    s.players.isEmpty() ->
                        NoPlayersState(
                            canEditClub = s.details.canEditClub,
                            hasClubPlayers = s.anyPlayers,
                            onAddPlayersToClub = { onCreatePlayers(clubName) },
                            onAddPlayersToAgeGroup = { onAddPlayersToAgeGroup(clubName) }
                        )
                    // Disabling and reactivating follow the club's MANAGE_PLAYER_STATUS setting, like the server.
                    // Reactivating needs no confirmation: it only lets the player be added to a pool again.
                    else -> PlayerList(
                        s.players,
                        onOpenPlayer,
                        onDisable = if (s.details.canManagePlayerStatus) { player -> confirmDisable = player } else null,
                        onReactivate = if (s.details.canManagePlayerStatus) { player -> onReactivate(player.id) } else null,
                        hasMore = s.hasMore,
                        loadingMore = s.loadingMore,
                        onLoadMore = onLoadMore
                    )
                }
            }
        }
    }
    confirmDisable?.let { player ->
        AlertDialog(
            onDismissRequest = { confirmDisable = null },
            title = { Text(stringResource(Res.string.title_disable_player, "${player.firstName} ${player.lastName}")) },
            text = { Text(stringResource(Res.string.msg_disable_player)) },
            confirmButton = {
                TextButton(onClick = { confirmDisable = null; onDisable(player.id) }) {
                    Text(stringResource(Res.string.btn_disable), color = AlertRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisable = null }) {
                    Text(stringResource(Res.string.btn_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

@Preview
@Composable
private fun ClubPlayersPreview() {
    val club = Club("c1", "CD Ribamar", status = ClubStatus.ACTIVE, createdAt = Instant.fromEpochMilliseconds(0), sport = SportType.FOOTBALL)
    val players = listOf(
        Player("p1", "c1", "Tomás", "Bragança", LocalDate(2012, 3, 1), shirtNumber = 10, active = true),
        Player("p2", "c1", "Rui", "Perestrelo", LocalDate(2012, 5, 9), active = true),
        Player("p3", "c1", "Duarte", "Nabais", LocalDate(2012, 8, 21), shirtNumber = 7, active = false)
    )
    val details = ClubDetails(club, emptyList(), emptyList(), emptyList(), canEditClub = true, canManagePlayerStatus = true)
    SquadifyTheme {
        ClubPlayersContent(
            ClubPlayersUiState.Idle(details, players = players, loaded = true, hasMore = true, loadingMore = true, anyPlayers = true),
            remember { SnackbarHostState() },
            {}, {}, { _, _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}
        )
    }
}

@Preview(widthDp = 900, heightDp = 480)
@Composable
private fun ClubPlayersLandscapePreview() {
    ClubPlayersPreview()
}

@Preview
@Composable
private fun ClubPlayersErrorPreview() {
    SquadifyTheme {
        ClubPlayersContent(
            ClubPlayersUiState.Error("Couldn't load the players"), remember { SnackbarHostState() },
            {}, {}, { _, _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}
        )
    }
}
