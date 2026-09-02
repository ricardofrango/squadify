package com.ricardofrango.footballmanager.presentation.ui.clubdetails

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ricardofrango.footballmanager.model.Player
import com.ricardofrango.footballmanager.model.Team
import com.ricardofrango.footballmanager.presentation.ui.theme.CarbonGray
import com.ricardofrango.footballmanager.presentation.ui.theme.DeepCarbon
import com.ricardofrango.footballmanager.presentation.ui.theme.PitchGreen
import footballmanager.app.shared.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClubDetailsScreen(
    clubId: String,
    onNavigateBack: () -> Unit,
    viewModel: ClubDetailsViewModel = koinViewModel()
) {
    val uiState by viewModel.state.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(clubId) {
        viewModel.loadClubDetails(clubId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = (uiState as? ClubDetailsUiState.Idle)?.details?.club?.name ?: "Club Details"
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_back),
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    var showAddMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showAddMenu = true }) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_more),
                                contentDescription = "More options",
                                tint = Color.White
                            )
                        }
                        DropdownMenu(
                            expanded = showAddMenu,
                            onDismissRequest = { showAddMenu = false },
                            modifier = Modifier.background(Color.White)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Create Team", color = DeepCarbon) },
                                onClick = { showAddMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Create Age Group", color = DeepCarbon) },
                                onClick = { showAddMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Add Season", color = DeepCarbon) },
                                onClick = { showAddMenu = false }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CarbonGray,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
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
                    onClick = { selectedTab = 0 },
                    icon = { 
                        Icon(
                            painter = painterResource(Res.drawable.ic_teams),
                            contentDescription = "Teams",
                            modifier = Modifier.size(24.dp)
                        ) 
                    },
                    label = { 
                        Text(
                            text = "Teams",
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
                    onClick = { selectedTab = 1 },
                    icon = { 
                        Icon(
                            painter = painterResource(Res.drawable.ic_player),
                            contentDescription = "Players",
                            modifier = Modifier.size(24.dp)
                        ) 
                    },
                    label = { 
                        Text(
                            text = "Players",
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
                is ClubDetailsUiState.Idle -> {
                    if (state.error != null) {
                        ErrorState(state.error, onRetry = { viewModel.loadClubDetails(clubId) })
                    } else if (state.details != null) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            if (selectedTab == 0) {
                                // Filters for Teams
                                SelectorFilterSection(
                                    label1 = "Season",
                                    selectedOption1 = state.details.seasons.find { it.id == state.selectedSeasonId }?.name ?: "All Seasons",
                                    options1 = listOf(null to "All Seasons") + state.details.seasons.map { it.id to it.name },
                                    onOption1Selected = viewModel::onSeasonSelected,
                                    
                                    label2 = "Age Group",
                                    selectedOption2 = state.details.ageGroups.find { it.id == state.selectedAgeGroupId }?.name ?: "All Ages",
                                    options2 = listOf(null to "All Ages") + state.details.ageGroups.map { it.id to it.name },
                                    onOption2Selected = viewModel::onAgeGroupSelected
                                )
                                TeamList(state.filteredTeams)
                            } else {
                                // Filters for Players
                                SelectorFilterSection(
                                    label1 = "Season",
                                    selectedOption1 = state.details.seasons.find { it.id == state.selectedPlayerSeasonId }?.name ?: "All Seasons",
                                    options1 = listOf(null to "All Seasons") + state.details.seasons.map { it.id to it.name },
                                    onOption1Selected = viewModel::onPlayerSeasonSelected,
                                    
                                    label2 = "Team",
                                    selectedOption2 = state.details.teams.find { it.id == state.selectedPlayerTeamId }?.name ?: "All Teams",
                                    options2 = listOf(null to "All Teams") + state.details.teams.map { it.id to it.name },
                                    onOption2Selected = viewModel::onPlayerTeamSelected
                                )
                                PlayerList(state.filteredPlayers)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectorFilterSection(
    label1: String,
    selectedOption1: String,
    options1: List<Pair<String?, String>>,
    onOption1Selected: (String?) -> Unit,
    label2: String,
    selectedOption2: String,
    options2: List<Pair<String?, String>>,
    onOption2Selected: (String?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FilterSelector(
            label = label1,
            selectedOption = selectedOption1,
            options = options1,
            onOptionSelected = onOption1Selected,
            modifier = Modifier.weight(1f)
        )

        FilterSelector(
            label = label2,
            selectedOption = selectedOption2,
            options = options2,
            onOptionSelected = onOption2Selected,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun <T> FilterSelector(
    label: String,
    selectedOption: String,
    options: List<Pair<T, String>>,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                color = DeepCarbon.copy(alpha = 0.8f)
            )
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable { expanded = true },
                shape = RoundedCornerShape(12.dp),
                color = CarbonGray.copy(alpha = 0.08f),
                border = null
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = selectedOption,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = DeepCarbon,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
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
}

@Composable
private fun TeamList(teams: List<Team>) {
    if (teams.isEmpty()) {
        EmptyState("No teams found for these filters")
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(teams) { team ->
                TeamCard(team)
            }
        }
    }
}

@Composable
private fun PlayerList(players: List<Player>) {
    if (players.isEmpty()) {
        EmptyState("No players found")
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(players) { player ->
                PlayerCard(player)
            }
        }
    }
}

@Composable
private fun TeamCard(team: Team) {
    Card(
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
private fun PlayerCard(player: Player) {
    Card(
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
                modifier = Modifier.size(40.dp).background(CarbonGray.copy(alpha = 0.1f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "${player.firstName.take(1)}${player.lastName.take(1)}", color = DeepCarbon)
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
                        text = "Number: $it", 
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
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text = message, color = DeepCarbon.copy(alpha = 0.6f))
    }
}
