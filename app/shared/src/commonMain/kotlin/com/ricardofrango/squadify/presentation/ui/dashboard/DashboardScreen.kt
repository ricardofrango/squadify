package com.ricardofrango.squadify.presentation.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.model.SportType
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import squadify.app.shared.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToClub: (String) -> Unit,
    onNavigateToAccount: () -> Unit,
    viewModel: DashboardViewModel = koinViewModel()
) {
    val uiState by viewModel.state.collectAsState()
    val isRefreshing = (uiState as? DashboardUiState.Idle)?.isRefreshing == true

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.title_my_clubs),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToAccount) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_account),
                            contentDescription = "Account",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = CarbonGray,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    )
{ paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.White)
        ) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize()
            ) {
                when (val state = uiState) {
                    is DashboardUiState.Loading -> {
                        // Cold start / retry-after-error only -- a real full-screen spinner, not the
                        // small pull-to-refresh indicator (that's reserved for an explicit pull).
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = PitchGreen)
                        }
                    }
                    is DashboardUiState.Error -> {
                        // No club list to show underneath a cold-start failure -- a blocking,
                        // full-screen retry state, not an error squeezed into Idle.
                        ErrorState(state.message, onRetry = { viewModel.loadClubs() })
                    }
                    is DashboardUiState.Idle -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // A failed pull-to-refresh: the existing list is real idle content, so
                            // the error shows inline alongside it (same idea as Login's inline error)
                            // instead of replacing the screen.
                            if (state.error != null) {
                                Text(
                                    text = state.error,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                if (state.clubs.isEmpty()) {
                                    EmptyState()
                                } else {
                                    ClubList(state.clubs, onNavigateToClub)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClubList(clubs: List<Club>, onNavigateToClub: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(clubs) { club ->
            ClubCard(club, onClick = { onNavigateToClub(club.id) })
        }
    }
}

@Composable
private fun ClubCard(club: Club, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Placeholder for Logo
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = CarbonGray.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = club.shortName ?: club.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium.copy(color = CarbonGray)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = club.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = DeepCarbon
                    )
                )
                Text(
                    text = stringResource(Res.string.label_active_member),
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            SportBadge(club.sport)
        }
    }
}

@Composable
private fun SportBadge(sport: SportType) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = PitchGreen.copy(alpha = 0.12f)
    ) {
        Text(
            text = stringResource(
                when (sport) {
                    SportType.FOOTBALL -> Res.string.sport_football
                    SportType.FUTSAL -> Res.string.sport_futsal
                }
            ),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = PitchGreen,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
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
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = stringResource(Res.string.msg_no_clubs), color = Color.Gray)
    }
}
