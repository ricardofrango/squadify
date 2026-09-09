package com.ricardofrango.clubmanager.presentation.ui.teamdetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.clubmanager.domain.usecase.GetTeamDetailsUseCase
import clubmanager.app.shared.generated.resources.Res
import clubmanager.app.shared.generated.resources.msg_failed_to_load_team
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

class TeamDetailsViewModel(
    private val getTeamDetailsUseCase: GetTeamDetailsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<TeamDetailsUiState>(TeamDetailsUiState.Loading)
    val state: StateFlow<TeamDetailsUiState> = _state.asStateFlow()

    fun load(teamId: String, forceReload: Boolean = false) {
        if (!forceReload && _state.value is TeamDetailsUiState.Idle) return

        viewModelScope.launch {
            _state.update { TeamDetailsUiState.Loading }
            try {
                _state.update { TeamDetailsUiState.Idle(getTeamDetailsUseCase(teamId)) }
            } catch (e: Exception) {
                // Per AI_GUIDELINES "No Raw Exception Messages in UI" -- never surface e.message.
                _state.update { TeamDetailsUiState.Error(getString(Res.string.msg_failed_to_load_team)) }
            }
        }
    }
}
