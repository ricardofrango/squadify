package com.ricardofrango.squadify.presentation.ui.createteams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.data.remote.ApiException
import com.ricardofrango.squadify.domain.usecase.CreateTeamsUseCase
import com.ricardofrango.squadify.domain.usecase.GetAgeGroupsUseCase
import com.ricardofrango.squadify.domain.usecase.GetSeasonsUseCase
import com.ricardofrango.squadify.model.CreateTeamsRequest
import com.ricardofrango.squadify.model.TeamItemRequest
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.msg_create_teams_failed
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

class CreateTeamsViewModel(
    private val createTeamsUseCase: CreateTeamsUseCase,
    private val getSeasonsUseCase: GetSeasonsUseCase,
    private val getAgeGroupsUseCase: GetAgeGroupsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<CreateTeamsUiState>(CreateTeamsUiState.Loading)
    val state: StateFlow<CreateTeamsUiState> = _state.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<CreateTeamsNavigationEvent>()
    val navigationEvent: SharedFlow<CreateTeamsNavigationEvent> = _navigationEvent.asSharedFlow()

    fun load(clubId: String) {
        if (_state.value is CreateTeamsUiState.Idle) return

        viewModelScope.launch {
            val seasons = try {
                getSeasonsUseCase(clubId)
            } catch (e: Exception) {
                emptyList()
            }
            val ageGroups = try {
                getAgeGroupsUseCase(clubId)
            } catch (e: Exception) {
                emptyList()
            }
            // Default to the most recent season by end date -- the one a coordinator is most likely
            // setting teams up for. Still changeable via the picker.
            val selectedSeasonId = seasons.maxByOrNull { it.endDate }?.id
            _state.update {
                CreateTeamsUiState.Idle(
                    seasons = seasons,
                    selectedSeasonId = selectedSeasonId,
                    ageGroups = ageGroups
                )
            }
        }
    }

    fun onSeasonSelected(seasonId: String) {
        _state.update { state ->
            if (state is CreateTeamsUiState.Idle) {
                // The previously picked age group belongs to the old season -- drop it so the user
                // re-picks one that exists under the new season.
                state.copy(selectedSeasonId = seasonId, selectedAgeGroupId = null, error = null)
            } else {
                state
            }
        }
    }

    fun onAgeGroupSelected(ageGroupId: String) {
        _state.update { state ->
            if (state is CreateTeamsUiState.Idle) state.copy(selectedAgeGroupId = ageGroupId, error = null) else state
        }
    }

    fun clearSubmitError() {
        _state.update { state ->
            if (state is CreateTeamsUiState.Idle) state.copy(error = null) else state
        }
    }

    fun onCreateClicked(clubId: String, items: List<TeamItemRequest>) {
        val current = _state.value as? CreateTeamsUiState.Idle ?: return
        val seasonId = current.selectedSeasonId ?: return
        val ageGroupId = current.selectedAgeGroupId ?: return

        viewModelScope.launch {
            _state.update { current.copy(isSubmitting = true, error = null) }
            try {
                createTeamsUseCase(CreateTeamsRequest(clubId, seasonId, ageGroupId, items))
                _navigationEvent.emit(CreateTeamsNavigationEvent.NavigateBackWithTeamsCreated)
            } catch (e: ApiException) {
                _state.update {
                    current.copy(isSubmitting = false, error = getString(Res.string.msg_create_teams_failed))
                }
            } catch (e: Exception) {
                _state.update {
                    current.copy(isSubmitting = false, error = getString(Res.string.msg_create_teams_failed))
                }
            }
        }
    }
}
