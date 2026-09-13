package com.ricardofrango.squadify.presentation.ui.createagegroups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.data.remote.ApiException
import com.ricardofrango.squadify.domain.usecase.CreateAgeGroupsUseCase
import com.ricardofrango.squadify.domain.usecase.GetAgeGroupsUseCase
import com.ricardofrango.squadify.domain.usecase.GetSeasonsUseCase
import com.ricardofrango.squadify.model.AgeGroupItemRequest
import com.ricardofrango.squadify.model.CreateAgeGroupsRequest
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.msg_age_group_conflict
import squadify.app.shared.generated.resources.msg_create_age_groups_failed
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

class CreateAgeGroupsViewModel(
    private val createAgeGroupsUseCase: CreateAgeGroupsUseCase,
    private val getAgeGroupsUseCase: GetAgeGroupsUseCase,
    private val getSeasonsUseCase: GetSeasonsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<CreateAgeGroupsUiState>(CreateAgeGroupsUiState.Loading)
    val state: StateFlow<CreateAgeGroupsUiState> = _state.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<CreateAgeGroupsNavigationEvent>()
    val navigationEvent: SharedFlow<CreateAgeGroupsNavigationEvent> = _navigationEvent.asSharedFlow()

    fun load(clubId: String, initialSeasonId: String?) {
        if (_state.value is CreateAgeGroupsUiState.Idle) return

        viewModelScope.launch {
            val seasons = try {
                getSeasonsUseCase(clubId)
            } catch (e: Exception) {
                emptyList()
            }
            val ageGroups = try {
                getAgeGroupsUseCase(clubId)
            } catch (e: Exception) {
                // The form is still usable without this -- it only pre-empts a conflict the server
                // would otherwise reject, it doesn't gate submission.
                emptyList()
            }
            // Honour the caller's pre-selection when it points at a real season; otherwise start on
            // the most recently started one so the form isn't blank. Either way the picker can change
            // it -- from the overflow menu the caller passes null, so this just seeds a sensible
            // default the user can override.
            val selectedSeasonId = initialSeasonId?.takeIf { id -> seasons.any { it.id == id } }
                ?: seasons.maxByOrNull { it.startDate }?.id
            _state.update {
                CreateAgeGroupsUiState.Idle(
                    seasons = seasons,
                    selectedSeasonId = selectedSeasonId,
                    ageGroups = ageGroups
                )
            }
        }
    }

    fun onSeasonSelected(seasonId: String) {
        _state.update { state ->
            if (state is CreateAgeGroupsUiState.Idle) state.copy(selectedSeasonId = seasonId, error = null) else state
        }
    }

    fun clearSubmitError() {
        _state.update { state ->
            if (state is CreateAgeGroupsUiState.Idle) state.copy(error = null) else state
        }
    }

    fun onCreateClicked(clubId: String, items: List<AgeGroupItemRequest>) {
        val current = _state.value as? CreateAgeGroupsUiState.Idle ?: return
        val seasonId = current.selectedSeasonId ?: return

        viewModelScope.launch {
            _state.update { current.copy(isSubmitting = true, error = null) }
            try {
                createAgeGroupsUseCase(CreateAgeGroupsRequest(clubId, seasonId, items))
                _navigationEvent.emit(CreateAgeGroupsNavigationEvent.NavigateBackWithAgeGroupsCreated)
            } catch (e: ApiException) {
                // The server can only report the first conflicting row today (see ISSUES TO FIX.md),
                // so this can't name a specific row the way the season screen's conflict message does
                // -- it just points back at the birth year fields to review.
                val message = if (e.statusCode == 409) {
                    getString(Res.string.msg_age_group_conflict)
                } else {
                    getString(Res.string.msg_create_age_groups_failed)
                }
                _state.update { current.copy(isSubmitting = false, error = message) }
            } catch (e: Exception) {
                _state.update {
                    current.copy(isSubmitting = false, error = getString(Res.string.msg_create_age_groups_failed))
                }
            }
        }
    }
}
