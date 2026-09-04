package com.ricardofrango.footballmanager.presentation.ui.createseason

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.footballmanager.data.remote.ApiException
import com.ricardofrango.footballmanager.domain.usecase.CreateSeasonUseCase
import com.ricardofrango.footballmanager.domain.usecase.GetSeasonsUseCase
import com.ricardofrango.footballmanager.model.CreateSeasonRequest
import com.ricardofrango.footballmanager.model.SeasonType
import footballmanager.app.shared.generated.resources.Res
import footballmanager.app.shared.generated.resources.msg_create_season_failed
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.getString

class CreateSeasonViewModel(
    private val createSeasonUseCase: CreateSeasonUseCase,
    private val getSeasonsUseCase: GetSeasonsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<CreateSeasonUiState>(CreateSeasonUiState.Loading)
    val state: StateFlow<CreateSeasonUiState> = _state.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<CreateSeasonNavigationEvent>()
    val navigationEvent: SharedFlow<CreateSeasonNavigationEvent> = _navigationEvent.asSharedFlow()

    fun loadExistingSeasons(clubId: String) {
        if (_state.value is CreateSeasonUiState.Idle) return

        viewModelScope.launch {
            val seasons = try {
                getSeasonsUseCase(clubId)
            } catch (e: Exception) {
                // The form is fully usable without this list -- it only enriches the 409 conflict
                // message with the colliding season's name, which falls back to a generic message.
                emptyList()
            }
            _state.update { CreateSeasonUiState.Idle(existingSeasons = seasons) }
        }
    }

    /** Clears any error/conflict left over from a previous submit once the user changes a field. */
    fun clearSubmitError() {
        _state.update { state ->
            if (state is CreateSeasonUiState.Idle) {
                state.copy(error = null, isConflict = false, conflictingSeasonName = null)
            } else {
                state
            }
        }
    }

    fun onCreateClicked(
        clubId: String,
        type: SeasonType,
        startDate: LocalDate,
        endDate: LocalDate,
        name: String?
    ) {
        val current = _state.value as? CreateSeasonUiState.Idle ?: return

        viewModelScope.launch {
            _state.update {
                current.copy(isSubmitting = true, error = null, isConflict = false, conflictingSeasonName = null)
            }
            try {
                createSeasonUseCase(
                    CreateSeasonRequest(
                        clubId = clubId,
                        name = name?.takeIf { it.isNotBlank() },
                        type = type,
                        startDate = startDate,
                        endDate = endDate
                    )
                )
                _navigationEvent.emit(CreateSeasonNavigationEvent.NavigateBackWithSeasonCreated)
            } catch (e: ApiException) {
                if (e.statusCode == 409) {
                    // The server's uniqueness check is exactly club+type+startDate+endDate, so the
                    // season it collided with is whichever local season matches those same fields.
                    val collidesWith = current.existingSeasons.find {
                        it.type == type && it.startDate == startDate && it.endDate == endDate
                    }
                    _state.update {
                        current.copy(
                            isSubmitting = false,
                            isConflict = true,
                            conflictingSeasonName = collidesWith?.name
                        )
                    }
                } else {
                    _state.update {
                        current.copy(isSubmitting = false, error = getString(Res.string.msg_create_season_failed))
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    current.copy(isSubmitting = false, error = getString(Res.string.msg_create_season_failed))
                }
            }
        }
    }
}
