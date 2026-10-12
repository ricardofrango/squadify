package com.ricardofrango.squadify.presentation.ui.agegroupdetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.domain.usecase.GetAgeGroupDetailsUseCase
import com.ricardofrango.squadify.domain.usecase.GetAgeGroupSeasonRecordUseCase
import com.ricardofrango.squadify.domain.usecase.GetTrainingPlansUseCase
import com.ricardofrango.squadify.domain.usecase.GetTrainingSessionsUseCase
import com.ricardofrango.squadify.data.remote.ApiException
import io.ktor.http.HttpStatusCode
import com.ricardofrango.squadify.model.StatsFilter
import com.ricardofrango.squadify.presentation.errorMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.msg_failed_to_load_age_group

class AgeGroupDetailsViewModel(
    private val getAgeGroupDetailsUseCase: GetAgeGroupDetailsUseCase,
    private val getAgeGroupSeasonRecordUseCase: GetAgeGroupSeasonRecordUseCase,
    private val getTrainingPlansUseCase: GetTrainingPlansUseCase,
    private val getTrainingSessionsUseCase: GetTrainingSessionsUseCase
) : ViewModel() {

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

    fun load(ageGroupId: String, forceReload: Boolean = false) {
        if (!forceReload && _state.value is AgeGroupDetailsUiState.Idle) return
        loadRecord(ageGroupId)
        loadPlans(ageGroupId)
        loadSessions(ageGroupId)

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

    fun onTabSelected(tab: AgeGroupTab) {
        _tab.value = tab
    }

    fun onFilterSelected(filter: StatsFilter) = _record.update {
        (it as? AgeGroupRecordUiState.Loaded)?.copy(filter = filter) ?: it
    }

    fun onMatchesTeamSelected(teamId: String?) {
        _matchesTeamId.value = teamId
    }
}
