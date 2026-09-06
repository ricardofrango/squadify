package com.ricardofrango.footballmanager.presentation.ui.clubdetails

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ricardofrango.footballmanager.model.Player
import com.ricardofrango.footballmanager.model.Team
import com.ricardofrango.footballmanager.presentation.ui.components.EmptyStateCallToAction
import com.ricardofrango.footballmanager.presentation.ui.components.FMTopBar
import com.ricardofrango.footballmanager.presentation.ui.theme.CarbonGray
import com.ricardofrango.footballmanager.presentation.ui.theme.DeepCarbon
import com.ricardofrango.footballmanager.presentation.ui.theme.PitchGreen
import footballmanager.app.shared.generated.resources.Res
import footballmanager.app.shared.generated.resources.btn_retry
import footballmanager.app.shared.generated.resources.ic_age_group
import footballmanager.app.shared.generated.resources.ic_calendar
import footballmanager.app.shared.generated.resources.ic_more
import footballmanager.app.shared.generated.resources.ic_player
import footballmanager.app.shared.generated.resources.ic_teams
import footballmanager.app.shared.generated.resources.label_add_season
import footballmanager.app.shared.generated.resources.label_all_ages
import footballmanager.app.shared.generated.resources.label_all_seasons
import footballmanager.app.shared.generated.resources.label_all_teams
import footballmanager.app.shared.generated.resources.label_create_age_group
import footballmanager.app.shared.generated.resources.label_create_team
import footballmanager.app.shared.generated.resources.label_more_options
import footballmanager.app.shared.generated.resources.label_number_prefix
import footballmanager.app.shared.generated.resources.msg_no_players_filter
import footballmanager.app.shared.generated.resources.msg_no_age_groups_admin_subtitle
import footballmanager.app.shared.generated.resources.msg_no_age_groups_member_subtitle
import footballmanager.app.shared.generated.resources.msg_no_age_groups_title
import footballmanager.app.shared.generated.resources.msg_no_seasons_admin_subtitle
import footballmanager.app.shared.generated.resources.msg_no_seasons_member_subtitle
import footballmanager.app.shared.generated.resources.msg_no_seasons_title
import footballmanager.app.shared.generated.resources.msg_no_teams_filter
import footballmanager.app.shared.generated.resources.tab_players
import footballmanager.app.shared.generated.resources.tab_teams
import footballmanager.app.shared.generated.resources.title_club_details
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClubDetailsScreen(
    clubId: String,
    onNavigateBack: () -> Unit,
    onNavigateToTeam: (String) -> Unit,
    onNavigateToPlayer: (String) -> Unit,
    // Takes the club's name because CreateSeasonScreen/CreateAgeGroupsScreen show it in their header,
    // and the only place that name is loaded is here.
    onNavigateToCreateSeason: (clubName: String) -> Unit,
    // The Create Age Groups screen has its own season picker, so it only needs an optional
    // pre-selection: a concrete season id when the entry point already implies one (a specific
    // season's "no age groups" state), or null from the overflow menu where the user should choose.
    onNavigateToCreateAgeGroups: (clubName: String, initialSeasonId: String?) -> Unit,
    // True exactly once, right after returning from CreateSeasonScreen/CreateAgeGroupsScreen having
    // created something -- forces a reload past loadClubDetails' "already have this club" guard, since
    // Koin scopes this ViewModel to the nav back-stack entry (so popping back here reuses stale state).
    refreshClubDetails: Boolean = false,
    viewModel: ClubDetailsViewModel = koinViewModel()
) {
    val uiState by viewModel.state.collectAsState()
    val selectedTab = (uiState as? ClubDetailsUiState.Idle)?.selectedTab ?: 0
    val clubName = (uiState as? ClubDetailsUiState.Idle)?.details?.club?.name ?: ""

    LaunchedEffect(clubId) {
        viewModel.loadClubDetails(clubId)
    }

    LaunchedEffect(refreshClubDetails) {
        if (refreshClubDetails) {
            viewModel.loadClubDetails(clubId, forceReload = true)
        }
    }

    Scaffold(
        topBar = {
            FMTopBar(
                title = (uiState as? ClubDetailsUiState.Idle)?.details?.club?.name
                    ?: stringResource(Res.string.title_club_details),
                onNavigateBack = onNavigateBack,
                actions = {
                    var showAddMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showAddMenu = true }) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_more),
                                contentDescription = stringResource(Res.string.label_more_options),
                                tint = Color.White
                            )
                        }
                        DropdownMenu(
                            expanded = showAddMenu,
                            onDismissRequest = { showAddMenu = false },
                            modifier = Modifier.background(Color.White)
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(Res.string.label_create_team),
                                        color = DeepCarbon
                                    )
                                },
                                onClick = { showAddMenu = false }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(Res.string.label_create_age_group),
                                        color = DeepCarbon
                                    )
                                },
                                onClick = {
                                    showAddMenu = false
                                    // The Create Age Groups screen picks the season itself; from here
                                    // the user hasn't indicated one, so pass no pre-selection. Still
                                    // no-ops if the club has no seasons at all yet -- create one first.
                                    val hasSeasons = (uiState as? ClubDetailsUiState.Idle)
                                        ?.details?.seasons?.isNotEmpty() == true
                                    if (hasSeasons) onNavigateToCreateAgeGroups(clubName, null)
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(Res.string.label_add_season),
                                        color = DeepCarbon
                                    )
                                },
                                onClick = {
                                    showAddMenu = false
                                    onNavigateToCreateSeason(clubName)
                                }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = CarbonGray,
                tonalElevation = 0.dp,
                windowInsets = NavigationBarDefaults.windowInsets
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { viewModel.onTabSelected(0) },
                    icon = {
                        Icon(
                            painter = painterResource(Res.drawable.ic_teams),
                            contentDescription = stringResource(Res.string.tab_teams),
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(Res.string.tab_teams),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PitchGreen,
                        selectedTextColor = PitchGreen,
                        unselectedIconColor = Color.White.copy(alpha = 0.5f),
                        unselectedTextColor = Color.White.copy(alpha = 0.5f),
                        indicatorColor = PitchGreen.copy(alpha = 0.15f)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { viewModel.onTabSelected(1) },
                    icon = {
                        Icon(
                            painter = painterResource(Res.drawable.ic_player),
                            contentDescription = stringResource(Res.string.tab_players),
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(Res.string.tab_players),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PitchGreen,
                        selectedTextColor = PitchGreen,
                        unselectedIconColor = Color.White.copy(alpha = 0.5f),
                        unselectedTextColor = Color.White.copy(alpha = 0.5f),
                        indicatorColor = PitchGreen.copy(alpha = 0.15f)
                    )
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.White)
        ) {
            when (val state = uiState) {
                is ClubDetailsUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = PitchGreen)
                    }
                }

                is ClubDetailsUiState.Idle -> when {
                    state.error != null ->
                        ErrorState(state.error, onRetry = { viewModel.loadClubDetails(clubId) })
                    // Everything below (teams, players, filters) is season-scoped, so with no seasons
                    // there is nothing meaningful to show. Only an admin/coordinator can fix this
                    // (CreateSeasonUseCase requires COORDINATOR+), so they get offered a way to create
                    // one; a plain member just sees that there are none yet.
                    state.showNoSeasonsState ->
                        NoSeasonsState(
                            canEditClub = state.canEditClub,
                            onCreateSeasonClick = { onNavigateToCreateSeason(clubName) }
                        )

                    // One level down from the above: seasons exist, but nothing has been organised
                    // into age groups yet, so teams and players still have nothing to attach to. Same
                    // permission gate as seasons (CreateAgeGroupsUseCase also requires COORDINATOR+).
                    state.showNoAgeGroupsState ->
                        NoAgeGroupsState(
                            canEditClub = state.canEditClub,
                            onCreateAgeGroupClick = {
                                // Whole club has no age groups yet: seed the screen's picker with the
                                // most recently started season, still overridable there.
                                onNavigateToCreateAgeGroups(
                                    clubName,
                                    state.details?.seasons?.maxByOrNull { it.startDate }?.id
                                )
                            }
                        )

                    state.details != null ->
                        ClubDetailsTabContent(
                            state = state,
                            selectedTab = selectedTab,
                            viewModel = viewModel,
                            onNavigateToTeam = onNavigateToTeam,
                            onNavigateToPlayer = onNavigateToPlayer,
                            onNavigateToCreateAgeGroups = onNavigateToCreateAgeGroups,
                            clubName = clubName
                        )
                }
            }
        }
    }
}

@Composable
private fun ClubDetailsTabContent(
    state: ClubDetailsUiState.Idle,
    selectedTab: Int,
    viewModel: ClubDetailsViewModel,
    onNavigateToTeam: (String) -> Unit,
    onNavigateToPlayer: (String) -> Unit,
    onNavigateToCreateAgeGroups: (clubName: String, initialSeasonId: String?) -> Unit,
    clubName: String
) {
    Column(modifier = Modifier.fillMaxSize()) {
        when (selectedTab) {
            0 -> {
                TeamFilterSection(state = state, viewModel = viewModel)
                // The club-wide "no age groups" state above only fires when *no* season has any; once
                // some do, narrowing the season filter to one that still has none would otherwise just
                // show an empty team list with no hint that an age group is what's missing.
                if (state.selectedSeasonId != null &&
                    viewModel.getAvailableAgeGroupsForTeams(state).isEmpty()
                ) {
                    NoAgeGroupsState(
                        canEditClub = state.canEditClub,
                        onCreateAgeGroupClick = {
                            onNavigateToCreateAgeGroups(clubName, state.selectedSeasonId)
                        }
                    )
                } else {
                    TeamList(state.filteredTeams, onNavigateToTeam)
                }
            }

            else -> {
                PlayerFilterSection(state = state, viewModel = viewModel)
                if (state.selectedPlayerSeasonId != null &&
                    viewModel.getAvailableAgeGroupsForPlayers(state).isEmpty()
                ) {
                    NoAgeGroupsState(
                        canEditClub = state.canEditClub,
                        onCreateAgeGroupClick = {
                            onNavigateToCreateAgeGroups(clubName, state.selectedPlayerSeasonId)
                        }
                    )
                } else {
                    PlayerList(state.filteredPlayers, onNavigateToPlayer)
                }
            }
        }
    }
}

@Composable
private fun TeamFilterSection(
    state: ClubDetailsUiState.Idle,
    viewModel: ClubDetailsViewModel
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            FilterSelector(
                selectedOption = state.details?.seasons?.find { it.id == state.selectedSeasonId }?.name
                    ?: stringResource(Res.string.label_all_seasons),
                options = listOf(null to stringResource(Res.string.label_all_seasons)) +
                        (state.details?.seasons?.map { it.id to it.name } ?: emptyList()),
                onOptionSelected = viewModel::onSeasonSelected
            )
        }
        item {
            FilterSelector(
                selectedOption = state.details?.ageGroups?.find { it.id == state.selectedAgeGroupId }?.name
                    ?: stringResource(Res.string.label_all_ages),
                options = listOf(null to stringResource(Res.string.label_all_ages)) +
                        viewModel.getAvailableAgeGroupsForTeams(state).map { it.id to it.name },
                onOptionSelected = viewModel::onAgeGroupSelected
            )
        }
    }
}

@Composable
private fun PlayerFilterSection(
    state: ClubDetailsUiState.Idle,
    viewModel: ClubDetailsViewModel
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            FilterSelector(
                selectedOption = state.details?.seasons?.find { it.id == state.selectedPlayerSeasonId }?.name
                    ?: stringResource(Res.string.label_all_seasons),
                options = listOf(null to stringResource(Res.string.label_all_seasons)) +
                        (state.details?.seasons?.map { it.id to it.name } ?: emptyList()),
                onOptionSelected = viewModel::onPlayerSeasonSelected
            )
        }
        item {
            FilterSelector(
                selectedOption = state.details?.ageGroups?.find { it.id == state.selectedPlayerAgeGroupId }?.name
                    ?: stringResource(Res.string.label_all_ages),
                options = listOf(null to stringResource(Res.string.label_all_ages)) +
                        viewModel.getAvailableAgeGroupsForPlayers(state).map { it.id to it.name },
                onOptionSelected = viewModel::onPlayerAgeGroupSelected
            )
        }
        item {
            FilterSelector(
                selectedOption = state.details?.teams?.find { it.id == state.selectedPlayerTeamId }?.name
                    ?: stringResource(Res.string.label_all_teams),
                options = listOf(null to stringResource(Res.string.label_all_teams)) +
                        viewModel.getAvailableTeamsForPlayers(state).map { it.id to it.name },
                onOptionSelected = viewModel::onPlayerTeamSelected
            )
        }
    }
}

@Composable
private fun <T> FilterSelector(
    selectedOption: String,
    options: List<Pair<T, String>>,
    onOptionSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Surface(
            modifier = Modifier
                .height(44.dp)
                .clickable { expanded = true },
            shape = RoundedCornerShape(12.dp),
            color = CarbonGray.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.1f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = selectedOption,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = DeepCarbon,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("▾", color = DeepCarbon, fontWeight = FontWeight.Bold)
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color.White)
        ) {
            options.forEach { (value, name) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = name,
                            color = if (selectedOption == name) PitchGreen else DeepCarbon,
                            fontWeight = if (selectedOption == name) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onOptionSelected(value)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun TeamList(teams: List<Team>, onNavigateToTeam: (String) -> Unit) {
    if (teams.isEmpty()) {
        EmptyState(stringResource(Res.string.msg_no_teams_filter))
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(teams) { team ->
                TeamCard(team, onClick = { onNavigateToTeam(team.id) })
            }
        }
    }
}

@Composable
private fun PlayerList(players: List<Player>, onNavigateToPlayer: (String) -> Unit) {
    if (players.isEmpty()) {
        EmptyState(stringResource(Res.string.msg_no_players_filter))
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(players) { player ->
                PlayerCard(player, onClick = { onNavigateToPlayer(player.id) })
            }
        }
    }
}

@Composable
private fun TeamCard(team: Team, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).background(PitchGreen, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = team.name.take(1), color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = team.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = DeepCarbon
            )
        }
    }
}

@Composable
private fun PlayerCard(player: Player, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp)
                    .background(CarbonGray.copy(alpha = 0.1f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${player.firstName.take(1)}${player.lastName.take(1)}",
                    color = DeepCarbon
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "${player.firstName} ${player.lastName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DeepCarbon
                )
                player.shirtNumber?.let {
                    Text(
                        text = stringResource(Res.string.label_number_prefix, it.toString()),
                        style = MaterialTheme.typography.bodySmall,
                        color = DeepCarbon.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorState(error: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = error, color = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) { Text(stringResource(Res.string.btn_retry)) }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text = message, color = DeepCarbon.copy(alpha = 0.6f))
    }
}

@Composable
private fun NoSeasonsState(canEditClub: Boolean, onCreateSeasonClick: () -> Unit) {
    EmptyStateCallToAction(
        icon = Res.drawable.ic_calendar,
        title = stringResource(Res.string.msg_no_seasons_title),
        subtitle = stringResource(
            if (canEditClub) Res.string.msg_no_seasons_admin_subtitle else Res.string.msg_no_seasons_member_subtitle
        ),
        canAct = canEditClub,
        actionLabel = stringResource(Res.string.label_add_season),
        onActionClick = onCreateSeasonClick
    )
}

@Composable
private fun NoAgeGroupsState(canEditClub: Boolean, onCreateAgeGroupClick: () -> Unit) {
    EmptyStateCallToAction(
        icon = Res.drawable.ic_age_group,
        title = stringResource(Res.string.msg_no_age_groups_title),
        subtitle = stringResource(
            if (canEditClub) Res.string.msg_no_age_groups_admin_subtitle else Res.string.msg_no_age_groups_member_subtitle
        ),
        canAct = canEditClub,
        actionLabel = stringResource(Res.string.label_create_age_group),
        onActionClick = onCreateAgeGroupClick
    )
}
