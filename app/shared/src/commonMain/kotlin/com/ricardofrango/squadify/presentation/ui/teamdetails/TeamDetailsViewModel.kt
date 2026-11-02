package com.ricardofrango.squadify.presentation.ui.teamdetails

import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import com.ricardofrango.squadify.domain.config.AppFeatures

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.domain.usecase.GetFullStatsUseCase
import com.ricardofrango.squadify.domain.usecase.GetTeamDetailsUseCase
import com.ricardofrango.squadify.domain.usecase.GetTeamSeasonRecordUseCase
import com.ricardofrango.squadify.domain.repository.UiPreferencesRepository
import com.ricardofrango.squadify.model.SquadSort
import com.ricardofrango.squadify.model.StatsFilter
import com.ricardofrango.squadify.presentation.errorMessage
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.msg_failed_to_load_team
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TeamDetailsViewModel(
    private val getTeamDetailsUseCase: GetTeamDetailsUseCase,
    private val getTeamSeasonRecordUseCase: GetTeamSeasonRecordUseCase,
    private val getFullStatsUseCase: GetFullStatsUseCase,
    private val preferences: UiPreferencesRepository,
    private val features: AppFeatures
) : ViewModel() {

    // Always opens on Matches -- the most common reason to open a team. (Design 32f remembered the
    // last-used tab; the developer chose a fixed starting tab instead.) Only the sort is remembered.
    private val _tab = MutableStateFlow(TeamTab.MATCHES)
    val tab: StateFlow<TeamTab> = _tab.asStateFlow()

    private val _squadSort = MutableStateFlow(
        preferences.get(KEY_SORT)?.let { name -> SquadSort.entries.firstOrNull { it.name == name } } ?: SquadSort.APPS
    )
    val squadSort: StateFlow<SquadSort> = _squadSort.asStateFlow()

    fun onTabSelected(tab: TeamTab) {
        _tab.value = tab
    }

    fun onSquadSortSelected(sort: SquadSort) {
        _squadSort.value = sort
        preferences.set(KEY_SORT, sort.name)
    }

    private val _state = MutableStateFlow<TeamDetailsUiState>(TeamDetailsUiState.Loading)
    val state: StateFlow<TeamDetailsUiState> = _state.asStateFlow()

    // Pull-to-refresh: the screen stays while it reloads; a failure is a one-off message.
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    private val _refreshFailed = MutableSharedFlow<Unit>()
    val refreshFailed: SharedFlow<Unit> = _refreshFailed.asSharedFlow()

    private val _record = MutableStateFlow<TeamRecordUiState>(TeamRecordUiState.Loading)
    val record: StateFlow<TeamRecordUiState> = _record.asStateFlow()

    fun load(teamId: String, forceReload: Boolean = false) {
        if (!forceReload && _state.value is TeamDetailsUiState.Idle) return
        loadRecord(teamId)

        viewModelScope.launch {
            _state.update { TeamDetailsUiState.Loading }
            try {
                val details = getTeamDetailsUseCase(teamId)
                // Without club members (the coach app), a staff chip opens no member's page.
                _state.update { TeamDetailsUiState.Idle(if (features.clubMembers) details else details.copy(canViewMembers = false)) }
            } catch (e: Exception) {
                // Per AI_GUIDELINES "No Raw Exception Messages in UI" -- never surface e.message.
                val message = errorMessage(e, Res.string.msg_failed_to_load_team)
                _state.update { TeamDetailsUiState.Error(message) }
            }
        }
    }

    /**
     * Pull-to-refresh: everything again with the screen left as it is (no full-screen spinner, no card
     * spinner). A failure keeps what's shown and says so once.
     */
    fun refresh(teamId: String) {
        if (_state.value !is TeamDetailsUiState.Idle || _isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                coroutineScope {
                    val record = async { runCatching { getTeamSeasonRecordUseCase(teamId) }.getOrNull() }
                    val fullStats = async { runCatching { getFullStatsUseCase.team(teamId) }.getOrNull() }
                    val fresh = getTeamDetailsUseCase(teamId)
                    _state.update {
                        if (it is TeamDetailsUiState.Idle) TeamDetailsUiState.Idle(if (features.clubMembers) fresh else fresh.copy(canViewMembers = false)) else it
                    }
                    record.await()?.let { r ->
                        val previous = _record.value as? TeamRecordUiState.Loaded
                        _record.update { TeamRecordUiState.Loaded(r, previous?.filter ?: StatsFilter.ALL, fullStats = fullStats.await() ?: previous?.fullStats) }
                    }
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

    /**
     * The match lists again, quietly (no spinner), on return to the app: they have no live socket, so a
     * game that started or finished meanwhile shows. A failure keeps what's shown.
     */
    fun refreshMatches(teamId: String) {
        if (_state.value !is TeamDetailsUiState.Idle) return
        viewModelScope.launch {
            val fresh = runCatching { getTeamDetailsUseCase(teamId) }.getOrNull() ?: return@launch
            _state.update { state ->
                (state as? TeamDetailsUiState.Idle)?.copy(
                    details = state.details.copy(
                        notPlayedMatches = fresh.notPlayedMatches,
                        upcomingMatches = fresh.upcomingMatches,
                        upcomingMatchScores = fresh.upcomingMatchScores,
                        recentResults = fresh.recentResults,
                        opponents = fresh.opponents
                    )
                ) ?: state
            }
        }
    }

    fun loadRecord(teamId: String) {
        val filter = (_record.value as? TeamRecordUiState.Loaded)?.filter ?: StatsFilter.ALL
        viewModelScope.launch {
            _record.update { TeamRecordUiState.Loading }
            try {
                _record.update { TeamRecordUiState.Loaded(getTeamSeasonRecordUseCase(teamId), filter) }
            } catch (e: Exception) {
                _record.update { TeamRecordUiState.Error }
                return@launch
            }
            // Team leaders' ranking counts trainings too, so it comes from the Full stats data. The
            // rest of the card shows meanwhile; if it fails, the ranking is left out.
            val fullStats = runCatching { getFullStatsUseCase.team(teamId) }.getOrNull() ?: return@launch
            _record.update { (it as? TeamRecordUiState.Loaded)?.copy(fullStats = fullStats) ?: it }
        }
    }

    fun onFilterSelected(filter: StatsFilter) = _record.update {
        (it as? TeamRecordUiState.Loaded)?.copy(filter = filter) ?: it
    }

    private companion object {
        const val KEY_SORT = "team_details_squad_sort"
    }
}
