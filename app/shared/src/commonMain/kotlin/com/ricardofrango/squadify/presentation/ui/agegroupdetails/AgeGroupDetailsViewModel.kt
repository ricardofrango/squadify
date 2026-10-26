package com.ricardofrango.squadify.presentation.ui.agegroupdetails

import com.ricardofrango.squadify.domain.repository.UiPreferencesRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.domain.usecase.GetAgeGroupDetailsUseCase
import com.ricardofrango.squadify.domain.usecase.GetAgeGroupSeasonRecordUseCase
import com.ricardofrango.squadify.domain.usecase.GetTrainingPlansUseCase
import com.ricardofrango.squadify.domain.usecase.GetTrainingSessionsUseCase
import com.ricardofrango.squadify.domain.usecase.RemovePlayerFromAgeGroupUseCase
import com.ricardofrango.squadify.domain.usecase.GetFullStatsUseCase
import com.ricardofrango.squadify.model.FullStatsData
import com.ricardofrango.squadify.data.remote.ApiException
import io.ktor.http.HttpStatusCode
import com.ricardofrango.squadify.model.StatsFilter
import com.ricardofrango.squadify.presentation.errorMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.msg_failed_to_load_age_group
import squadify.app.shared.generated.resources.msg_remove_player_failed

class AgeGroupDetailsViewModel(
    private val getAgeGroupDetailsUseCase: GetAgeGroupDetailsUseCase,
    private val getAgeGroupSeasonRecordUseCase: GetAgeGroupSeasonRecordUseCase,
    private val getTrainingPlansUseCase: GetTrainingPlansUseCase,
    private val getTrainingSessionsUseCase: GetTrainingSessionsUseCase,
    private val getFullStatsUseCase: GetFullStatsUseCase,
    private val removePlayerFromAgeGroupUseCase: RemovePlayerFromAgeGroupUseCase,
    private val preferences: UiPreferencesRepository
) : ViewModel() {

    // One-off results of removing a player, for the snackbar.
    private val _playerRemoval = MutableSharedFlow<PlayerRemovalResult>()
    val playerRemoval: SharedFlow<PlayerRemovalResult> = _playerRemoval.asSharedFlow()

    // The Stats tab's leaders card, from the Full stats data (games and trainings); null until loaded or on failure.
    private val _fullStats = MutableStateFlow<FullStatsData?>(null)
    val fullStats: StateFlow<FullStatsData?> = _fullStats.asStateFlow()

    private val _state = MutableStateFlow<AgeGroupDetailsUiState>(AgeGroupDetailsUiState.Loading)
    val state: StateFlow<AgeGroupDetailsUiState> = _state.asStateFlow()

    private val _plans = MutableStateFlow<AgeGroupPlansUiState>(AgeGroupPlansUiState.Loading)
    val plans: StateFlow<AgeGroupPlansUiState> = _plans.asStateFlow()

    private val _sessions = MutableStateFlow<AgeGroupSessionsUiState>(AgeGroupSessionsUiState.Loading)
    val sessions: StateFlow<AgeGroupSessionsUiState> = _sessions.asStateFlow()

    private val _record = MutableStateFlow<AgeGroupRecordUiState>(AgeGroupRecordUiState.Loading)
    val record: StateFlow<AgeGroupRecordUiState> = _record.asStateFlow()

    // Always opens on Stats (the developer's choice over design 33f's "Teams, then last used").
    private val _tab = MutableStateFlow(AgeGroupTab.STATS)
    val tab: StateFlow<AgeGroupTab> = _tab.asStateFlow()

    // The Matches tab's team filter (null = all teams). Not remembered (33f).
    private val _matchesTeamId = MutableStateFlow<String?>(null)
    val matchesTeamId: StateFlow<String?> = _matchesTeamId.asStateFlow()

    // The Training tab's sessions as a list or a calendar: the last choice, remembered on the device.
    private val _sessionsCalendar = MutableStateFlow(preferences.get(KEY_SESSIONS_VIEW) == SESSIONS_CALENDAR)
    val sessionsCalendar: StateFlow<Boolean> = _sessionsCalendar.asStateFlow()

    fun load(ageGroupId: String, forceReload: Boolean = false) {
        if (!forceReload && _state.value is AgeGroupDetailsUiState.Idle) return
        loadRecord(ageGroupId)
        loadPlans(ageGroupId)
        loadSessions(ageGroupId)
        viewModelScope.launch { _fullStats.value = runCatching { getFullStatsUseCase.ageGroup(ageGroupId) }.getOrNull() }

        viewModelScope.launch {
            _state.update { AgeGroupDetailsUiState.Loading }
            try {
                _state.update { AgeGroupDetailsUiState.Idle(getAgeGroupDetailsUseCase(ageGroupId)) }
            } catch (e: Exception) {
                val message = errorMessage(e, Res.string.msg_failed_to_load_age_group)
                _state.update { AgeGroupDetailsUiState.Error(message) }
            }
        }
    }

    fun loadRecord(ageGroupId: String) {
        val filter = (_record.value as? AgeGroupRecordUiState.Loaded)?.filter ?: StatsFilter.ALL
        viewModelScope.launch {
            _record.update { AgeGroupRecordUiState.Loading }
            try {
                _record.update { AgeGroupRecordUiState.Loaded(getAgeGroupSeasonRecordUseCase(ageGroupId), filter) }
            } catch (e: Exception) {
                _record.update { AgeGroupRecordUiState.Error }
            }
        }
    }

    /** The Training tab's plans; a 403 means the viewer doesn't see plans, so the cards are left out. */
    fun loadPlans(ageGroupId: String) {
        viewModelScope.launch {
            if (_plans.value !is AgeGroupPlansUiState.Loaded) _plans.update { AgeGroupPlansUiState.Loading }
            _plans.update {
                try {
                    AgeGroupPlansUiState.Loaded(getTrainingPlansUseCase(ageGroupId))
                } catch (e: ApiException) {
                    if (e.statusCode == HttpStatusCode.Forbidden.value) AgeGroupPlansUiState.Hidden else AgeGroupPlansUiState.Error
                } catch (e: Exception) {
                    AgeGroupPlansUiState.Error
                }
            }
        }
    }

    fun loadSessions(ageGroupId: String) {
        viewModelScope.launch {
            if (_sessions.value !is AgeGroupSessionsUiState.Loaded) _sessions.update { AgeGroupSessionsUiState.Loading }
            _sessions.update {
                try {
                    AgeGroupSessionsUiState.Loaded(getTrainingSessionsUseCase(ageGroupId))
                } catch (e: Exception) {
                    (it as? AgeGroupSessionsUiState.Loaded) ?: AgeGroupSessionsUiState.Error
                }
            }
        }
    }

    /**
     * Takes [playerId] out of this age group's pool (they stay in the club). The list drops them at
     * once; the season record and the leaders reload, since they're built from the pool.
     */
    fun removePlayer(ageGroupId: String, playerId: String) {
        val details = (_state.value as? AgeGroupDetailsUiState.Idle)?.details ?: return
        val player = details.players.firstOrNull { it.id == playerId } ?: return
        viewModelScope.launch {
            try {
                removePlayerFromAgeGroupUseCase(ageGroupId, playerId)
                _state.update { s -> (s as? AgeGroupDetailsUiState.Idle)?.let { it.copy(details = it.details.copy(players = it.details.players.filter { p -> p.id != playerId })) } ?: s }
                loadRecord(ageGroupId)
                _fullStats.value = runCatching { getFullStatsUseCase.ageGroup(ageGroupId) }.getOrNull()
                _playerRemoval.emit(PlayerRemovalResult.Removed("${player.firstName} ${player.lastName}", details.ageGroup.name))
            } catch (e: Exception) {
                _playerRemoval.emit(PlayerRemovalResult.Failed(errorMessage(e, Res.string.msg_remove_player_failed)))
            }
        }
    }

    fun onTabSelected(tab: AgeGroupTab) {
        _tab.value = tab
    }

    fun onFilterSelected(filter: StatsFilter) = _record.update {
        (it as? AgeGroupRecordUiState.Loaded)?.copy(filter = filter) ?: it
    }

    fun onMatchesTeamSelected(teamId: String?) {
        _matchesTeamId.value = teamId
    }

    fun onSessionsCalendar(calendar: Boolean) {
        _sessionsCalendar.value = calendar
        preferences.set(KEY_SESSIONS_VIEW, if (calendar) SESSIONS_CALENDAR else SESSIONS_LIST)
    }

    private companion object {
        const val KEY_SESSIONS_VIEW = "age_group_sessions_view"
        const val SESSIONS_CALENDAR = "calendar"
        const val SESSIONS_LIST = "list"
    }
}
