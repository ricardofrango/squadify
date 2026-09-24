package com.ricardofrango.squadify.presentation.ui.createplayers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.data.remote.ApiException
import com.ricardofrango.squadify.domain.usecase.CreatePlayersUseCase
import com.ricardofrango.squadify.model.CreatePlayer
import com.ricardofrango.squadify.model.CreatePlayersRequest
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.msg_create_players_failed
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

class CreatePlayersViewModel(
    private val createPlayersUseCase: CreatePlayersUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(CreatePlayersUiState())
    val state: StateFlow<CreatePlayersUiState> = _state.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<CreatePlayersNavigationEvent>()
    val navigationEvent: SharedFlow<CreatePlayersNavigationEvent> = _navigationEvent.asSharedFlow()

    fun clearSubmitError() {
        _state.update { it.copy(error = null) }
    }

    fun onCreateClicked(clubId: String, players: List<CreatePlayer>, ageGroupId: String? = null) {
        if (_state.value.isSubmitting || players.isEmpty()) return

        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null) }
            try {
                createPlayersUseCase(CreatePlayersRequest(clubId, players, ageGroupId))
                _navigationEvent.emit(CreatePlayersNavigationEvent.NavigateBackWithPlayersCreated)
            } catch (e: ApiException) {
                _state.update { it.copy(isSubmitting = false, error = getString(Res.string.msg_create_players_failed)) }
            } catch (e: Exception) {
                _state.update { it.copy(isSubmitting = false, error = getString(Res.string.msg_create_players_failed)) }
            }
        }
    }
}
