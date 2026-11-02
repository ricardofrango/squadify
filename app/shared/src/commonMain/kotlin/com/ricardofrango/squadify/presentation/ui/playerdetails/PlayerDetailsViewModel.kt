package com.ricardofrango.squadify.presentation.ui.playerdetails

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import com.ricardofrango.squadify.domain.config.AppFeatures

import com.ricardofrango.squadify.domain.usecase.TrainingStatsUseCase
import com.ricardofrango.squadify.model.PlayerTrainingSeason
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.domain.usecase.GetPlayerDetailsUseCase
import com.ricardofrango.squadify.domain.usecase.GetPlayerSeasonStatsUseCase
import com.ricardofrango.squadify.model.StatsFilter
import com.ricardofrango.squadify.presentation.errorMessage
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.msg_failed_to_load_player
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class PlayerDetailsViewModel(
    private val getPlayerDetailsUseCase: GetPlayerDetailsUseCase,
    private val getPlayerSeasonStatsUseCase: GetPlayerSeasonStatsUseCase,
    private val trainingStatsUseCase: TrainingStatsUseCase,
    private val features: AppFeatures
) : ViewModel() {

    // Training attendance and average per season (design 39j); empty for anyone who can't see it.
    private val _training = MutableStateFlow<List<PlayerTrainingSeason>>(emptyList())
    val training: StateFlow<List<PlayerTrainingSeason>> = _training.asStateFlow()

    private val _state = MutableStateFlow<PlayerDetailsUiState>(PlayerDetailsUiState.Loading)
    val state: StateFlow<PlayerDetailsUiState> = _state.asStateFlow()

    private val _stats = MutableStateFlow<SeasonStatsUiState>(SeasonStatsUiState.Loading)
    val stats: StateFlow<SeasonStatsUiState> = _stats.asStateFlow()

    /**
     * [initialSeasonId] / [initialFilter] come from Team Details' leaders (design 31f), so the player
     * opens on the team's season and filter; null opens on his current season and All.
     */
    fun loadPlayerDetails(playerId: String, initialSeasonId: String? = null, initialFilter: StatsFilter? = null) {
        viewModelScope.launch {
            _state.update { PlayerDetailsUiState.Loading }
            try {
                val profile = getPlayerDetailsUseCase(playerId)
                _state.update { PlayerDetailsUiState.Idle(profile = profile) }
            } catch (e: Exception) {
                val message = errorMessage(e, Res.string.msg_failed_to_load_player)
                _state.update { PlayerDetailsUiState.Error(message) }
            }
        }
        loadSeasonStats(playerId, initialSeasonId, initialFilter)
        // The coach app has no training: no call, and the card stays hidden.
        if (features.training) {
            viewModelScope.launch { _training.value = runCatching { trainingStatsUseCase.player(playerId).seasons }.getOrDefault(emptyList()) }
        }
    }

    fun loadSeasonStats(playerId: String, initialSeasonId: String? = null, initialFilter: StatsFilter? = null) {
        // A reload (retry, or returning from an edit) keeps whatever season/filter is on screen.
        val previous = _stats.value as? SeasonStatsUiState.Loaded
        viewModelScope.launch {
            _stats.update { SeasonStatsUiState.Loading }
            try {
                val stats = getPlayerSeasonStatsUseCase(playerId)
                val wanted = initialSeasonId ?: previous?.selectedSeasonId
                val seasonId = wanted?.takeIf { id -> stats.seasons.any { it.seasonId == id } } ?: stats.currentSeasonId
                _stats.update {
                    SeasonStatsUiState.Loaded(stats, seasonId, initialFilter ?: previous?.filter ?: StatsFilter.ALL)
                }
            } catch (e: Exception) {
                _stats.update { SeasonStatsUiState.Error }
            }
        }
    }

    // Pull-to-refresh: the screen stays while it reloads; a failure is a one-off message.
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    private val _refreshFailed = MutableSharedFlow<Unit>()
    val refreshFailed: SharedFlow<Unit> = _refreshFailed.asSharedFlow()

    /**
     * Pull-to-refresh: the profile, the season stats (keeping the season and filter on screen) and the
     * training, with the screen left as it is (no full-screen or card spinner). A failure keeps what's
     * shown and says so once.
     */
    fun refresh(playerId: String) {
        if (_state.value !is PlayerDetailsUiState.Idle || _isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                coroutineScope {
                    val stats = async { runCatching { getPlayerSeasonStatsUseCase(playerId) }.getOrNull() }
                    val training = if (features.training) async { runCatching { trainingStatsUseCase.player(playerId).seasons }.getOrNull() } else null
                    val profile = getPlayerDetailsUseCase(playerId)
                    _state.update { if (it is PlayerDetailsUiState.Idle) PlayerDetailsUiState.Idle(profile = profile) else it }
                    stats.await()?.let { fresh ->
                        _stats.update { current ->
                            val previous = current as? SeasonStatsUiState.Loaded
                            val seasonId = previous?.selectedSeasonId?.takeIf { id -> fresh.seasons.any { it.seasonId == id } } ?: fresh.currentSeasonId
                            SeasonStatsUiState.Loaded(fresh, seasonId, previous?.filter ?: StatsFilter.ALL)
                        }
                    }
                    training?.await()?.let { _training.value = it }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _refreshFailed.emit(Unit)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun onSeasonSelected(seasonId: String) = _stats.update {
        (it as? SeasonStatsUiState.Loaded)?.copy(selectedSeasonId = seasonId) ?: it
    }

    fun onFilterSelected(filter: StatsFilter) = _stats.update {
        (it as? SeasonStatsUiState.Loaded)?.copy(filter = filter) ?: it
    }
}
