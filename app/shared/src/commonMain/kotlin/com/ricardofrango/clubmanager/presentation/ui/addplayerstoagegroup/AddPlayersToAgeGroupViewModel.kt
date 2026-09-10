package com.ricardofrango.clubmanager.presentation.ui.addplayerstoagegroup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.clubmanager.data.remote.ApiException
import com.ricardofrango.clubmanager.domain.usecase.AddPlayersToAgeGroupUseCase
import com.ricardofrango.clubmanager.domain.usecase.GetAgeGroupsUseCase
import com.ricardofrango.clubmanager.domain.usecase.GetEligiblePlayersForAgeGroupUseCase
import com.ricardofrango.clubmanager.domain.usecase.GetSeasonsUseCase
import clubmanager.app.shared.generated.resources.Res
import clubmanager.app.shared.generated.resources.msg_add_players_failed
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

class AddPlayersToAgeGroupViewModel(
    private val getAgeGroupsUseCase: GetAgeGroupsUseCase,
    private val getSeasonsUseCase: GetSeasonsUseCase,
    private val getEligiblePlayersForAgeGroupUseCase: GetEligiblePlayersForAgeGroupUseCase,
    private val addPlayersToAgeGroupUseCase: AddPlayersToAgeGroupUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<AddPlayersToAgeGroupUiState>(AddPlayersToAgeGroupUiState.Loading)
    val state: StateFlow<AddPlayersToAgeGroupUiState> = _state.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<AddPlayersToAgeGroupNavigationEvent>()
    val navigationEvent: SharedFlow<AddPlayersToAgeGroupNavigationEvent> = _navigationEvent.asSharedFlow()

    fun load(clubId: String, initialAgeGroupId: String? = null) {
        if (_state.value is AddPlayersToAgeGroupUiState.Idle) return

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
            // Honour a caller-supplied pre-selection; otherwise default to the age group in the most
            // recently ending season (tiebreak oldest players).
            val selectedAgeGroupId = initialAgeGroupId?.takeIf { id -> ageGroups.any { it.id == id } }
                ?: ageGroups
                    .sortedWith(
                        compareByDescending<com.ricardofrango.clubmanager.model.AgeGroup> {
                            seasons.find { s -> s.id == it.seasonId }?.endDate
                        }.thenBy { it.birthYear }
                    )
                    .firstOrNull()
                    ?.id

            _state.update {
                AddPlayersToAgeGroupUiState.Idle(
                    ageGroups = ageGroups,
                    seasons = seasons,
                    selectedAgeGroupId = selectedAgeGroupId
                )
            }
            selectedAgeGroupId?.let { loadEligible(it) }
        }
    }

    fun onAgeGroupSelected(ageGroupId: String) {
        val current = _state.value as? AddPlayersToAgeGroupUiState.Idle ?: return
        if (ageGroupId == current.selectedAgeGroupId) return
        _state.update {
            current.copy(
                selectedAgeGroupId = ageGroupId,
                selectedPlayerIds = emptySet(),
                eligiblePlayers = emptyList(),
                loadingEligible = true,
                error = null
            )
        }
        loadEligible(ageGroupId)
    }

    private fun loadEligible(ageGroupId: String) {
        viewModelScope.launch {
            val players = try {
                getEligiblePlayersForAgeGroupUseCase(ageGroupId)
            } catch (e: Exception) {
                emptyList()
            }
            _state.update { state ->
                if (state is AddPlayersToAgeGroupUiState.Idle && state.selectedAgeGroupId == ageGroupId) {
                    state.copy(eligiblePlayers = players, loadingEligible = false)
                } else {
                    state
                }
            }
        }
    }

    /**
     * Re-fetch the eligible list for the currently selected age group, keeping the current
     * selection -- called when the user comes back from registering new club players via the
     * "New player" shortcut, so those players appear without leaving the screen.
     */
    fun refreshEligiblePlayers() {
        val current = _state.value as? AddPlayersToAgeGroupUiState.Idle ?: return
        val ageGroupId = current.selectedAgeGroupId ?: return
        _state.update { current.copy(loadingEligible = true, error = null) }
        loadEligible(ageGroupId)
    }

    fun onPlayerToggled(playerId: String) {
        _state.update { state ->
            if (state is AddPlayersToAgeGroupUiState.Idle) {
                val next = if (playerId in state.selectedPlayerIds) {
                    state.selectedPlayerIds - playerId
                } else {
                    state.selectedPlayerIds + playerId
                }
                state.copy(selectedPlayerIds = next, error = null)
            } else {
                state
            }
        }
    }

    fun clearError() {
        _state.update { state ->
            if (state is AddPlayersToAgeGroupUiState.Idle) state.copy(error = null) else state
        }
    }

    fun onAddClicked() {
        val current = _state.value as? AddPlayersToAgeGroupUiState.Idle ?: return
        val ageGroupId = current.selectedAgeGroupId ?: return
        if (current.selectedPlayerIds.isEmpty()) return

        viewModelScope.launch {
            _state.update { current.copy(isSubmitting = true, error = null) }
            try {
                addPlayersToAgeGroupUseCase(ageGroupId, current.selectedPlayerIds.toList())
                _navigationEvent.emit(AddPlayersToAgeGroupNavigationEvent.NavigateBackWithPlayersAdded)
            } catch (e: ApiException) {
                _state.update { current.copy(isSubmitting = false, error = getString(Res.string.msg_add_players_failed)) }
            } catch (e: Exception) {
                _state.update { current.copy(isSubmitting = false, error = getString(Res.string.msg_add_players_failed)) }
            }
        }
    }
}
