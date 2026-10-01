package com.ricardofrango.squadify.presentation.ui.teamdetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.domain.usecase.GetTeamDetailsUseCase
import com.ricardofrango.squadify.domain.usecase.GetTeamSeasonRecordUseCase
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
    private val getTeamSeasonRecordUseCase: GetTeamSeasonRecordUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<TeamDetailsUiState>(TeamDetailsUiState.Loading)
    val state: StateFlow<TeamDetailsUiState> = _state.asStateFlow()

    private val _record = MutableStateFlow<TeamRecordUiState>(TeamRecordUiState.Loading)
    val record: StateFlow<TeamRecordUiState> = _record.asStateFlow()

    fun load(teamId: String, forceReload: Boolean = false) {
        if (!forceReload && _state.value is TeamDetailsUiState.Idle) return
        loadRecord(teamId)

        viewModelScope.launch {
            _state.update { TeamDetailsUiState.Loading }
            try {
                _state.update { TeamDetailsUiState.Idle(getTeamDetailsUseCase(teamId)) }
            } catch (e: Exception) {
                // Per AI_GUIDELINES "No Raw Exception Messages in UI" -- never surface e.message.
                val message = errorMessage(e, Res.string.msg_failed_to_load_team)
                _state.update { TeamDetailsUiState.Error(message) }
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
            }
        }
    }

    fun onFilterSelected(filter: StatsFilter) = _record.update {
        (it as? TeamRecordUiState.Loaded)?.copy(filter = filter) ?: it
    }
}
