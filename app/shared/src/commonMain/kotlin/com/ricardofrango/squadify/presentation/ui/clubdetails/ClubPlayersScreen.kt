package com.ricardofrango.squadify.presentation.ui.clubdetails

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.model.SportType
import com.ricardofrango.squadify.presentation.ui.components.FMTopBar
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
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
    val state = viewModel.state.collectSettledState()
    LaunchedEffect(clubId) { viewModel.load(clubId) }
    LaunchedEffect(refresh) { if (refresh) viewModel.load(clubId, force = true) }

    val idle = state as? ClubPlayersUiState.Idle
    Scaffold(
        containerColor = Color.White,
        topBar = {
            FMTopBar(
                title = stringResource(Res.string.tab_players),
                subtitle = idle?.details?.club?.name,
                onNavigateBack = onNavigateBack
            )
        }
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding).background(Color.White)
        when (val s = state) {
            ClubPlayersUiState.Loading -> Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PitchGreen) }
            is ClubPlayersUiState.Error -> Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(s.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { viewModel.load(clubId, force = true) }) { Text(stringResource(Res.string.btn_retry)) }
            }
            is ClubPlayersUiState.Idle -> Column(modifier) {
                val clubName = s.details.club.name
                Filters(s, viewModel)
                when {
                    s.seasonId != null && s.availableAgeGroups.isEmpty() ->
                        NoAgeGroupsState(
                            canEditClub = s.details.canEditClub,
                            onCreateAgeGroupClick = { onCreateAgeGroups(clubName, s.details.club.sport, s.seasonId) }
                        )
                    s.players.isEmpty() ->
                        NoPlayersState(
                            canEditClub = s.details.canEditClub,
                            hasClubPlayers = s.details.players.isNotEmpty(),
                            onAddPlayersToClub = { onCreatePlayers(clubName) },
                            onAddPlayersToAgeGroup = { onAddPlayersToAgeGroup(clubName) }
                        )
                    else -> PlayerList(s.players, onOpenPlayer)
                }
            }
        }
    }
}

@Composable
private fun Filters(state: ClubPlayersUiState.Idle, viewModel: ClubPlayersViewModel) {
    val details = state.details
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            item {
                FilterSelector(
                    selectedOption = details.seasons.find { it.id == state.seasonId }?.name ?: stringResource(Res.string.label_all_seasons),
                    options = listOf(null to stringResource(Res.string.label_all_seasons)) + details.seasons.map { it.id to it.name },
                    onOptionSelected = viewModel::onSeasonSelected
                )
            }
            item {
                FilterSelector(
                    selectedOption = details.ageGroups.find { it.id == state.ageGroupId }?.name ?: stringResource(Res.string.label_all_ages),
                    options = listOf(null to stringResource(Res.string.label_all_ages)) + state.availableAgeGroups.map { it.id to it.name },
                    onOptionSelected = viewModel::onAgeGroupSelected
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onSearchChanged,
            label = { Text(stringResource(Res.string.label_search_players)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = DeepCarbon, unfocusedTextColor = DeepCarbon,
                focusedBorderColor = PitchGreen, unfocusedBorderColor = CarbonGray.copy(alpha = 0.3f),
                focusedLabelColor = PitchGreen, unfocusedLabelColor = CarbonGray
            )
        )
    }
}
