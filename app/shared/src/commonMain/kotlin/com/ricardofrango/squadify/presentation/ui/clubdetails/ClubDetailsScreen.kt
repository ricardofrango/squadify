package com.ricardofrango.squadify.presentation.ui.clubdetails

import kotlinx.datetime.LocalDate
import com.ricardofrango.squadify.model.TrainingPlanLevel
import com.ricardofrango.squadify.presentation.navigation.collectSettledState
import org.jetbrains.compose.resources.StringResource
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.SportType
import com.ricardofrango.squadify.model.Team
import com.ricardofrango.squadify.presentation.ui.components.EmptyStateCallToAction
import com.ricardofrango.squadify.presentation.ui.components.FMTopBar
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.components.labelRes
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.more_add_players
import squadify.app.shared.generated.resources.more_create_team
import squadify.app.shared.generated.resources.btn_retry
import squadify.app.shared.generated.resources.ic_account
import squadify.app.shared.generated.resources.ic_age_group
import squadify.app.shared.generated.resources.ic_calendar
import squadify.app.shared.generated.resources.ic_home
import squadify.app.shared.generated.resources.ic_more
import squadify.app.shared.generated.resources.ic_player
import squadify.app.shared.generated.resources.ic_teams
import squadify.app.shared.generated.resources.label_add_players_to_age_group
import squadify.app.shared.generated.resources.label_add_players_to_club
import squadify.app.shared.generated.resources.label_add_season
import squadify.app.shared.generated.resources.label_all_ages
import squadify.app.shared.generated.resources.label_all_seasons
import squadify.app.shared.generated.resources.label_create_age_group
import squadify.app.shared.generated.resources.label_create_staff
import squadify.app.shared.generated.resources.label_create_team
import squadify.app.shared.generated.resources.msg_edit_club_details
import squadify.app.shared.generated.resources.label_number_prefix
import squadify.app.shared.generated.resources.label_search_players
import squadify.app.shared.generated.resources.msg_no_players_admin_subtitle
import squadify.app.shared.generated.resources.msg_no_players_admin_subtitle_no_club_players
import squadify.app.shared.generated.resources.msg_no_players_filter
import squadify.app.shared.generated.resources.msg_no_age_groups_admin_subtitle
import squadify.app.shared.generated.resources.msg_no_age_groups_member_subtitle
import squadify.app.shared.generated.resources.msg_no_age_groups_title
import squadify.app.shared.generated.resources.msg_no_seasons_admin_subtitle
import squadify.app.shared.generated.resources.msg_no_seasons_member_subtitle
import squadify.app.shared.generated.resources.msg_no_seasons_title
import squadify.app.shared.generated.resources.msg_no_teams_admin_subtitle
import squadify.app.shared.generated.resources.msg_no_teams_filter
import squadify.app.shared.generated.resources.msg_no_teams_member_subtitle
import squadify.app.shared.generated.resources.label_age_group_summary
import squadify.app.shared.generated.resources.tab_age_groups
import squadify.app.shared.generated.resources.tab_home
import squadify.app.shared.generated.resources.tab_matches
import squadify.app.shared.generated.resources.tab_more
import squadify.app.shared.generated.resources.tab_players
import squadify.app.shared.generated.resources.tab_teams
import squadify.app.shared.generated.resources.title_account
import squadify.app.shared.generated.resources.title_club_details
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClubDetailsScreen(
    clubId: String,
    // False for the single-club user's root screen (see AppNavigation / Screen.ClubDetails.isRootClub)
    // -- Dashboard was popped off the back stack in that case, so there's nowhere to go back to.
    showBackButton: Boolean = true,
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
    // The Create Teams screen has its own season + age-group pickers; [ageGroupId] preselects one (a
    // coach's create action from the More panel is scoped to the age group they picked).
    onNavigateToCreateTeams: (clubName: String, sport: SportType, ageGroupId: String?) -> Unit,
    // The Add Players screen picks the age group itself.
    onNavigateToAddPlayersToAgeGroup: (clubName: String) -> Unit,
    onNavigateToCreatePlayers: (clubName: String, ageGroupId: String?) -> Unit,
    // Registers a brand-new person to the club (by email); assigning them a role in a specific age
    // group happens separately, from that age group's own screen.
    onNavigateToCreateStaff: (clubName: String) -> Unit,
    onNavigateToAgeGroup: (ageGroupId: String) -> Unit,
    onNavigateToMatch: (matchId: String) -> Unit,
    // Club Details is now the primary hub for every user (single-club users never see Dashboard, see
    // Screen.ClubDetails.isRootClub), so it carries its own direct route to Account/logout instead of
    // only being reachable from Dashboard's top bar.
    onNavigateToAccount: () -> Unit,
    onNavigateToEditClub: () -> Unit,
    // The club's player list -- its own screen, not a tab under this bottom bar.
    onNavigateToPlayers: () -> Unit = {},
    onNavigateToOpponents: () -> Unit = {},
    // A Home missing-plan alert: the plan editor of that age group and period.
    onNavigateToEditPlan: (ageGroupId: String, level: TrainingPlanLevel, periodStart: LocalDate) -> Unit = { _, _, _ -> },
    // The More panel's "Switch club" (2+ clubs) and Log out.
    onSwitchClub: () -> Unit = {},
    onLoggedOut: () -> Unit = {},
    // True exactly once, right after returning from a Create* screen having created something --
    // forces a reload past loadClubDetails' "already have this club" guard, since Koin scopes this
    // ViewModel to the nav back-stack entry (so popping back here reuses stale state).
    refreshClubDetails: Boolean = false,
    viewModel: ClubDetailsViewModel = koinViewModel()
) {
    val uiState = viewModel.state.collectSettledState()
    val selectedTab = (uiState as? ClubDetailsUiState.Idle)?.selectedTab ?: CLUB_TAB_HOME
    val clubName = (uiState as? ClubDetailsUiState.Idle)?.details?.club?.name ?: ""
    val sport = (uiState as? ClubDetailsUiState.Idle)?.details?.club?.sport ?: SportType.FOOTBALL
    val canEditClub = (uiState as? ClubDetailsUiState.Idle)?.canEditClub ?: false

    LaunchedEffect(clubId) {
        viewModel.loadClubDetails(clubId)
    }

    // Not saved with the back-stack entry on purpose: opening a screen from the panel leaves it open
    // while that screen slides in over it, and coming back recreates this screen with the panel closed.
    var moreOpen by remember { mutableStateOf(false) }
    // The coach's age-group picker: which action it's for (Create team / Add players), or null.
    var pickerFor by remember { mutableStateOf<StringResource?>(null) }
    val moreAccount by viewModel.moreAccount.collectAsState()
    LaunchedEffect(moreOpen) { if (moreOpen) viewModel.loadMoreAccount() }

    LaunchedEffect(refreshClubDetails) {
        if (refreshClubDetails) {
            viewModel.loadClubDetails(clubId, forceReload = true)
        }
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            FMTopBar(
                title = (uiState as? ClubDetailsUiState.Idle)?.details?.club?.name
                    ?: stringResource(Res.string.title_club_details),
                showBackButton = showBackButton,
                onNavigateBack = onNavigateBack
                // No account icon here any more: Account lives at the top of the More panel (design 37).
            )
        },
        bottomBar = {
            // Only meaningful once the club details actually loaded -- while loading, or if the
            // fetch failed (Idle.details stays null), there's nothing for the tabs to show.
            if ((uiState as? ClubDetailsUiState.Idle)?.details != null) {
                NavigationBar(
                    containerColor = CarbonGray,
                    tonalElevation = 0.dp,
                    windowInsets = NavigationBarDefaults.windowInsets
                ) {
                    ClubTabItem(
                        selected = selectedTab == CLUB_TAB_HOME,
                        onClick = { viewModel.onTabSelected(CLUB_TAB_HOME) },
                        icon = Res.drawable.ic_home,
                        label = stringResource(Res.string.tab_home)
                    )
                    ClubTabItem(
                        selected = selectedTab == CLUB_TAB_AGE_GROUPS,
                        onClick = { viewModel.onTabSelected(CLUB_TAB_AGE_GROUPS) },
                        icon = Res.drawable.ic_age_group,
                        label = stringResource(Res.string.tab_age_groups)
                    )
                    ClubTabItem(
                        selected = selectedTab == CLUB_TAB_TEAMS,
                        onClick = { viewModel.onTabSelected(CLUB_TAB_TEAMS) },
                        icon = Res.drawable.ic_teams,
                        label = stringResource(Res.string.tab_teams)
                    )
                    ClubTabItem(
                        selected = selectedTab == CLUB_TAB_MATCHES,
                        onClick = { viewModel.onTabSelected(CLUB_TAB_MATCHES) },
                        icon = Res.drawable.ic_calendar,
                        label = stringResource(Res.string.tab_matches)
                    )

                    // "More" opens the full-screen panel (design 37) -- Players, the create actions and
                    // Account all live there. Shown as selected while the panel is open.
                    ClubTabItem(
                        selected = moreOpen,
                        onClick = { moreOpen = !moreOpen },
                        icon = Res.drawable.ic_more,
                        label = stringResource(Res.string.tab_more)
                    )
                }
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
                            onNavigateToEditPlan = onNavigateToEditPlan,
                            clubName = clubName
                        )
                }
            }
        }
    }

    val details = (uiState as? ClubDetailsUiState.Idle)?.details
    if (details != null) {
        val groups = moreGroups(
            details = details,
            // Players is its own screen now; it slides in over the open panel like the other rows.
            onPlayers = onNavigateToPlayers,
            onOpponents = onNavigateToOpponents,
            onClubSettings = onNavigateToEditClub,
            onAddSeason = { onNavigateToCreateSeason(clubName) },
            onCreateAgeGroup = { if (details.seasons.isNotEmpty()) onNavigateToCreateAgeGroups(clubName, sport, null) },
            onCreateTeam = { scoped ->
                when {
                    !scoped -> onNavigateToCreateTeams(clubName, sport, null)
                    details.createTeamAgeGroupIds.size == 1 -> onNavigateToCreateTeams(clubName, sport, details.createTeamAgeGroupIds.single())
                    // The picker is a dialog over the club screen, so the panel closes first.
                    else -> { moreOpen = false; pickerFor = Res.string.more_create_team }
                }
            },
            onAddPlayers = { scoped ->
                when {
                    !scoped -> onNavigateToCreatePlayers(clubName, null)
                    details.createPlayersAgeGroupIds.size == 1 -> onNavigateToCreatePlayers(clubName, details.createPlayersAgeGroupIds.single())
                    else -> { moreOpen = false; pickerFor = Res.string.more_add_players }
                }
            },
            onNewStaff = { onNavigateToCreateStaff(clubName) },
            onAccount = onNavigateToAccount,
            onSwitchClub = onSwitchClub,
            clubCount = moreAccount?.clubCount ?: 1
        )
        ClubMorePanel(
            open = moreOpen,
            account = moreAccount,
            viewer = MoreViewer(details.viewerRole, details.viewerAgeGroups, details.club.name),
            groups = groups,
            onOpenAccount = onNavigateToAccount,
            onLogOut = { moreOpen = false; viewModel.logout(onLoggedOut) },
            onClose = { moreOpen = false }
        )
        pickerFor?.let { action ->
            val ids = if (action == Res.string.more_create_team) details.createTeamAgeGroupIds else details.createPlayersAgeGroupIds
            AgeGroupPickerDialog(
                title = action,
                ageGroups = details.ageGroups.filter { it.id in ids }.sortedByDescending { it.endYear },
                teamCount = { agId -> details.teams.count { it.ageGroupId == agId } },
                onPick = { ag ->
                    pickerFor = null
                    if (action == Res.string.more_create_team) onNavigateToCreateTeams(clubName, sport, ag.id)
                    else onNavigateToCreatePlayers(clubName, ag.id)
                },
                onDismiss = { pickerFor = null }
            )
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
    onNavigateToCreateTeams: (clubName: String, sport: SportType, ageGroupId: String?) -> Unit,
    sport: SportType,
    onNavigateToCreatePlayers: (clubName: String, ageGroupId: String?) -> Unit,
    onNavigateToAddPlayersToAgeGroup: (clubName: String) -> Unit,
    onNavigateToAgeGroup: (ageGroupId: String) -> Unit,
    onNavigateToMatch: (matchId: String) -> Unit,
    onNavigateToEditPlan: (ageGroupId: String, level: TrainingPlanLevel, periodStart: LocalDate) -> Unit,
    clubName: String
) {
    Column(modifier = Modifier.fillMaxSize()) {
        when (selectedTab) {
            CLUB_TAB_HOME -> HomeTab(
                home = state.home,
                onRetry = { viewModel.loadClubDetails(clubId, forceReload = true) },
                onNavigateToAgeGroup = onNavigateToAgeGroup,
                onNavigateToMatch = onNavigateToMatch,
                onEditPlan = onNavigateToEditPlan
            )

            CLUB_TAB_TEAMS -> {
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
                            onCreateTeamClick = { onNavigateToCreateTeams(clubName, sport, null) }
                        )

                    else -> TeamList(state.filteredTeams, onNavigateToTeam)
                }
            }

            CLUB_TAB_AGE_GROUPS -> {
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
    ageGroups: List<com.ricardofrango.squadify.model.AgeGroup>,
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
    ageGroup: com.ricardofrango.squadify.model.AgeGroup,
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
                    // Single-year groups show just their year (e.g. "15"); a two-year span shows
                    // both, older first, matching the season-name convention (e.g. "16/17").
                    text = if (ageGroup.startYear == ageGroup.endYear) {
                        ageGroup.endYear.toString().takeLast(2)
                    } else {
                        "${ageGroup.endYear.toString().takeLast(2)}/${ageGroup.startYear.toString().takeLast(2)}"
                    },
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
internal fun PlayerList(players: List<Player>, onNavigateToPlayer: (String) -> Unit) {
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
                val caption = listOfNotNull(
                    player.shirtNumber?.let { stringResource(Res.string.label_number_prefix, it.toString()) },
                    player.position?.let { stringResource(it.labelRes()) }
                ).joinToString(" · ")
                if (caption.isNotEmpty()) {
                    Text(
                        text = caption,
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
internal fun NoAgeGroupsState(canEditClub: Boolean, onCreateAgeGroupClick: () -> Unit) {
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

// Shown on the Players screen (ClubPlayersScreen) when no player matches the active filters. Two-step model: players are
// registered on the club first, then added to age-group pools. [hasClubPlayers] picks which step the
// button points at; a plain member with no edit rights just gets a "adjust the filters" nudge.
@Composable
internal fun NoPlayersState(
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
