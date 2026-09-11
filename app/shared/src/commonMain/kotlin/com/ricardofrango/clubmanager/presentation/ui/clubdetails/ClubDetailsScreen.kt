package com.ricardofrango.clubmanager.presentation.ui.clubdetails

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import com.ricardofrango.clubmanager.model.Player
import com.ricardofrango.clubmanager.model.SportType
import com.ricardofrango.clubmanager.model.Team
import com.ricardofrango.clubmanager.presentation.ui.components.EmptyStateCallToAction
import com.ricardofrango.clubmanager.presentation.ui.components.FMTopBar
import com.ricardofrango.clubmanager.presentation.ui.theme.CarbonGray
import com.ricardofrango.clubmanager.presentation.ui.theme.DeepCarbon
import com.ricardofrango.clubmanager.presentation.ui.theme.PitchGreen
import clubmanager.app.shared.generated.resources.Res
import clubmanager.app.shared.generated.resources.btn_retry
import clubmanager.app.shared.generated.resources.ic_age_group
import clubmanager.app.shared.generated.resources.ic_calendar
import clubmanager.app.shared.generated.resources.ic_home
import clubmanager.app.shared.generated.resources.ic_more
import clubmanager.app.shared.generated.resources.ic_player
import clubmanager.app.shared.generated.resources.ic_teams
import clubmanager.app.shared.generated.resources.label_add_players_to_age_group
import clubmanager.app.shared.generated.resources.label_add_players_to_club
import clubmanager.app.shared.generated.resources.label_add_season
import clubmanager.app.shared.generated.resources.label_all_ages
import clubmanager.app.shared.generated.resources.label_all_seasons
import clubmanager.app.shared.generated.resources.label_create_age_group
import clubmanager.app.shared.generated.resources.label_create_team
import clubmanager.app.shared.generated.resources.label_more_options
import clubmanager.app.shared.generated.resources.label_number_prefix
import clubmanager.app.shared.generated.resources.msg_no_players_admin_subtitle
import clubmanager.app.shared.generated.resources.msg_no_players_admin_subtitle_no_club_players
import clubmanager.app.shared.generated.resources.msg_no_players_filter
import clubmanager.app.shared.generated.resources.msg_no_age_groups_admin_subtitle
import clubmanager.app.shared.generated.resources.msg_no_age_groups_member_subtitle
import clubmanager.app.shared.generated.resources.msg_no_age_groups_title
import clubmanager.app.shared.generated.resources.msg_no_seasons_admin_subtitle
import clubmanager.app.shared.generated.resources.msg_no_seasons_member_subtitle
import clubmanager.app.shared.generated.resources.msg_no_seasons_title
import clubmanager.app.shared.generated.resources.msg_no_teams_admin_subtitle
import clubmanager.app.shared.generated.resources.msg_no_teams_filter
import clubmanager.app.shared.generated.resources.msg_no_teams_member_subtitle
import clubmanager.app.shared.generated.resources.label_age_group_summary
import clubmanager.app.shared.generated.resources.tab_age_groups
import clubmanager.app.shared.generated.resources.tab_home
import clubmanager.app.shared.generated.resources.tab_matches
import clubmanager.app.shared.generated.resources.tab_players
import clubmanager.app.shared.generated.resources.tab_teams
import clubmanager.app.shared.generated.resources.title_club_details
import org.jetbrains.compose.resources.DrawableResource
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
    // Takes the club's name because CreateSeasonScreen/CreateAgeGroupsScreen/CreateTeamsScreen show it
    // in their header, and the only place that name is loaded is here.
    onNavigateToCreateSeason: (clubName: String) -> Unit,
    // The Create Age Groups screen has its own season picker, so it only needs an optional
    // pre-selection: a concrete season id when the entry point already implies one (a specific
    // season's "no age groups" state), or null from the overflow menu where the user should choose.
    onNavigateToCreateAgeGroups: (clubName: String, sport: SportType, initialSeasonId: String?) -> Unit,
    // The Create Teams screen has its own season + age-group pickers, so this only needs the club name + sport.
    onNavigateToCreateTeams: (clubName: String, sport: SportType) -> Unit,
    // The Add Players screen picks the age group itself.
    onNavigateToAddPlayersToAgeGroup: (clubName: String) -> Unit,
    onNavigateToCreatePlayers: (clubName: String) -> Unit,
    onNavigateToAgeGroup: (ageGroupId: String) -> Unit,
    onNavigateToMatch: (matchId: String) -> Unit,
    // True exactly once, right after returning from a Create* screen having created something --
    // forces a reload past loadClubDetails' "already have this club" guard, since Koin scopes this
    // ViewModel to the nav back-stack entry (so popping back here reuses stale state).
    refreshClubDetails: Boolean = false,
    viewModel: ClubDetailsViewModel = koinViewModel()
) {
    val uiState by viewModel.state.collectAsState()
    val selectedTab = (uiState as? ClubDetailsUiState.Idle)?.selectedTab ?: CLUB_TAB_HOME
    val clubName = (uiState as? ClubDetailsUiState.Idle)?.details?.club?.name ?: ""
    val sport = (uiState as? ClubDetailsUiState.Idle)?.details?.club?.sport ?: SportType.FOOTBALL

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
                                onClick = {
                                    showAddMenu = false
                                    onNavigateToCreateTeams(clubName, sport)
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(Res.string.label_add_players_to_club),
                                        color = DeepCarbon
                                    )
                                },
                                onClick = {
                                    showAddMenu = false
                                    onNavigateToCreatePlayers(clubName)
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(Res.string.label_add_players_to_age_group),
                                        color = DeepCarbon
                                    )
                                },
                                onClick = {
                                    showAddMenu = false
                                    onNavigateToAddPlayersToAgeGroup(clubName)
                                }
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
                                    if (hasSeasons) onNavigateToCreateAgeGroups(clubName, sport, null)
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
                ClubTabItem(
                    selected = selectedTab == 0,
                    onClick = { viewModel.onTabSelected(0) },
                    icon = Res.drawable.ic_age_group,
                    label = stringResource(Res.string.tab_age_groups)
                )
                ClubTabItem(
                    selected = selectedTab == 1,
                    onClick = { viewModel.onTabSelected(1) },
                    icon = Res.drawable.ic_teams,
                    label = stringResource(Res.string.tab_teams)
                )
                ClubTabItem(
                    selected = selectedTab == 2,
                    onClick = { viewModel.onTabSelected(2) },
                    icon = Res.drawable.ic_home,
                    label = stringResource(Res.string.tab_home)
                )
                ClubTabItem(
                    selected = selectedTab == 3,
                    onClick = { viewModel.onTabSelected(3) },
                    icon = Res.drawable.ic_player,
                    label = stringResource(Res.string.tab_players)
                )
                ClubTabItem(
                    selected = selectedTab == 4,
                    onClick = { viewModel.onTabSelected(4) },
                    icon = Res.drawable.ic_calendar,
                    label = stringResource(Res.string.tab_matches)
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
                                    sport,
                                    state.details?.seasons?.maxByOrNull { it.startDate }?.id
                                )
                            }
                        )

                    state.details != null ->
                        ClubDetailsTabContent(
                            state = state,
                            selectedTab = selectedTab,
                            clubId = clubId,
                            viewModel = viewModel,
                            onNavigateToTeam = onNavigateToTeam,
                            onNavigateToPlayer = onNavigateToPlayer,
                            onNavigateToCreateAgeGroups = onNavigateToCreateAgeGroups,
                            onNavigateToCreateTeams = onNavigateToCreateTeams,
                            sport = sport,
                            onNavigateToCreatePlayers = onNavigateToCreatePlayers,
                            onNavigateToAddPlayersToAgeGroup = onNavigateToAddPlayersToAgeGroup,
                            onNavigateToAgeGroup = onNavigateToAgeGroup,
                            onNavigateToMatch = onNavigateToMatch,
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
    clubId: String,
    viewModel: ClubDetailsViewModel,
    onNavigateToTeam: (String) -> Unit,
    onNavigateToPlayer: (String) -> Unit,
    onNavigateToCreateAgeGroups: (clubName: String, sport: SportType, initialSeasonId: String?) -> Unit,
    onNavigateToCreateTeams: (clubName: String, sport: SportType) -> Unit,
    sport: SportType,
    onNavigateToCreatePlayers: (clubName: String) -> Unit,
    onNavigateToAddPlayersToAgeGroup: (clubName: String) -> Unit,
    onNavigateToAgeGroup: (ageGroupId: String) -> Unit,
    onNavigateToMatch: (matchId: String) -> Unit,
    clubName: String
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Tab order: 0 Age groups, 1 Teams, 2 Home (default), 3 Players, 4 Matches.
        when (selectedTab) {
            2 -> HomeTab(
                home = state.home,
                onRetry = { viewModel.loadClubDetails(clubId, forceReload = true) },
                onNavigateToAgeGroup = onNavigateToAgeGroup,
                onNavigateToMatch = onNavigateToMatch
            )

            1 -> {
                TeamFilterSection(state = state, viewModel = viewModel)
                // The club-wide "no age groups" state above only fires when *no* season has any; once
                // some do, narrowing the season filter to one that still has none would otherwise just
                // show an empty team list with no hint that an age group is what's missing.
                when {
                    state.selectedSeasonId != null &&
                        viewModel.getAvailableAgeGroupsForTeams(state).isEmpty() ->
                        NoAgeGroupsState(
                            canEditClub = state.canEditClub,
                            onCreateAgeGroupClick = {
                                onNavigateToCreateAgeGroups(clubName, sport, state.selectedSeasonId)
                            }
                        )

                    // Age groups exist for this scope but no team matches the current filters --
                    // offer a coordinator a way to add one (CreateTeamsUseCase requires COORDINATOR+).
                    state.filteredTeams.isEmpty() ->
                        NoTeamsState(
                            canEditClub = state.canEditClub,
                            onCreateTeamClick = { onNavigateToCreateTeams(clubName, sport) }
                        )

                    else -> TeamList(state.filteredTeams, onNavigateToTeam)
                }
            }

            3 -> {
                PlayerFilterSection(state = state, viewModel = viewModel)
                when {
                    state.selectedPlayerSeasonId != null &&
                        viewModel.getAvailableAgeGroupsForPlayers(state).isEmpty() ->
                        NoAgeGroupsState(
                            canEditClub = state.canEditClub,
                            onCreateAgeGroupClick = {
                                onNavigateToCreateAgeGroups(clubName, sport, state.selectedPlayerSeasonId)
                            }
                        )

                    // No player matches the current filters. If the club has no players at all the
                    // first step is to register them; otherwise it's assigning existing players to an
                    // age group's pool (both require COORDINATOR+; an age-group coach can also assign).
                    state.filteredPlayers.isEmpty() ->
                        NoPlayersState(
                            canEditClub = state.canEditClub,
                            hasClubPlayers = state.details?.players?.isNotEmpty() == true,
                            onAddPlayersToClub = { onNavigateToCreatePlayers(clubName) },
                            onAddPlayersToAgeGroup = { onNavigateToAddPlayersToAgeGroup(clubName) }
                        )

                    else -> PlayerList(state.filteredPlayers, onNavigateToPlayer)
                }
            }

            0 -> {
                AgeGroupsFilterSection(state = state, viewModel = viewModel)
                val ageGroups = viewModel.getAgeGroupsForAgeGroupsTab(state)
                if (ageGroups.isEmpty()) {
                    // Some other season has age groups (the club-wide "none at all" case is handled
                    // above), but the selected one doesn't -- offer creating one in that season.
                    NoAgeGroupsState(
                        canEditClub = state.canEditClub,
                        onCreateAgeGroupClick = {
                            onNavigateToCreateAgeGroups(clubName, sport, state.selectedAgeGroupsSeasonId)
                        }
                    )
                } else {
                    AgeGroupList(
                        ageGroups = ageGroups,
                        teamCountFor = { ag -> state.details?.teams?.count { it.ageGroupId == ag } ?: 0 },
                        playerCountFor = { ag ->
                            state.details?.ageGroupPlayers?.count { it.ageGroupId == ag } ?: 0
                        },
                        onAgeGroupClick = onNavigateToAgeGroup
                    )
                }
            }

            // Matches — the club-wide fixture list (see MatchesTab.kt).
            else -> MatchesTab(
                state = state,
                viewModel = viewModel,
                onGoToAgeGroups = { viewModel.onTabSelected(CLUB_TAB_AGE_GROUPS) },
                onNavigateToMatch = onNavigateToMatch
            )
        }
    }
}

@Composable
private fun RowScope.ClubTabItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: DrawableResource,
    label: String
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            Icon(
                painter = painterResource(icon),
                contentDescription = label,
                modifier = Modifier.size(24.dp)
            )
        },
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
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

@Composable
private fun AgeGroupsFilterSection(
    state: ClubDetailsUiState.Idle,
    viewModel: ClubDetailsViewModel
) {
    val seasons = state.details?.seasons ?: return
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            // Season only, and no "All seasons": an age group only means something within one season.
            FilterSelector(
                selectedOption = seasons.find { it.id == state.selectedAgeGroupsSeasonId }?.name
                    ?: seasons.firstOrNull()?.name.orEmpty(),
                options = seasons.map { it.id to it.name },
                onOptionSelected = viewModel::onAgeGroupsSeasonSelected
            )
        }
    }
}

@Composable
private fun AgeGroupList(
    ageGroups: List<com.ricardofrango.clubmanager.model.AgeGroup>,
    teamCountFor: (ageGroupId: String) -> Int,
    playerCountFor: (ageGroupId: String) -> Int,
    onAgeGroupClick: (ageGroupId: String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(ageGroups) { ageGroup ->
            AgeGroupCard(
                ageGroup = ageGroup,
                teamCount = teamCountFor(ageGroup.id),
                playerCount = playerCountFor(ageGroup.id),
                onClick = { onAgeGroupClick(ageGroup.id) }
            )
        }
    }
}

@Composable
private fun AgeGroupCard(
    ageGroup: com.ricardofrango.clubmanager.model.AgeGroup,
    teamCount: Int,
    playerCount: Int,
    onClick: () -> Unit
) {
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
                Text(
                    text = ageGroup.birthYear.toString().takeLast(2),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = ageGroup.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DeepCarbon
                )
                Text(
                    text = stringResource(Res.string.label_age_group_summary, teamCount, playerCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = DeepCarbon.copy(alpha = 0.6f)
                )
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
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
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
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
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
    }
}

@Composable
internal fun <T> FilterSelector(
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
    // Callers only reach here with a non-empty list -- the empty case is handled one level up by
    // NoTeamsState (which also offers a coordinator the "create a team" action).
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

@Composable
private fun PlayerList(players: List<Player>, onNavigateToPlayer: (String) -> Unit) {
    // Callers only reach here with a non-empty list -- the empty case is handled one level up by
    // NoPlayersState (which also offers the right "add players" action).
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

// Shown on the Teams tab when age groups exist for the current scope but no team matches the active
// filters. Unlike the two states above this isn't a structural gap -- a plain member may just need to
// change the filters -- but a coordinator gets offered the same create action as the overflow menu.
@Composable
private fun NoTeamsState(canEditClub: Boolean, onCreateTeamClick: () -> Unit) {
    EmptyStateCallToAction(
        icon = Res.drawable.ic_teams,
        title = stringResource(Res.string.msg_no_teams_filter),
        subtitle = stringResource(
            if (canEditClub) Res.string.msg_no_teams_admin_subtitle else Res.string.msg_no_teams_member_subtitle
        ),
        canAct = canEditClub,
        actionLabel = stringResource(Res.string.label_create_team),
        onActionClick = onCreateTeamClick
    )
}

// Shown on the Players tab when no player matches the active filters. Two-step model: players are
// registered on the club first, then added to age-group pools. [hasClubPlayers] picks which step the
// button points at; a plain member with no edit rights just gets a "adjust the filters" nudge.
@Composable
private fun NoPlayersState(
    canEditClub: Boolean,
    hasClubPlayers: Boolean,
    onAddPlayersToClub: () -> Unit,
    onAddPlayersToAgeGroup: () -> Unit
) {
    EmptyStateCallToAction(
        icon = Res.drawable.ic_player,
        title = stringResource(Res.string.msg_no_players_filter),
        subtitle = stringResource(
            when {
                !canEditClub -> Res.string.msg_no_teams_member_subtitle
                hasClubPlayers -> Res.string.msg_no_players_admin_subtitle
                else -> Res.string.msg_no_players_admin_subtitle_no_club_players
            }
        ),
        canAct = canEditClub,
        actionLabel = stringResource(
            if (hasClubPlayers) Res.string.label_add_players_to_age_group
            else Res.string.label_add_players_to_club
        ),
        onActionClick = if (hasClubPlayers) onAddPlayersToAgeGroup else onAddPlayersToClub
    )
}
