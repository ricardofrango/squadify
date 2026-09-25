package com.ricardofrango.squadify.presentation.ui.playerdetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.domain.usecase.GetPlayerDetailsUseCase
import com.ricardofrango.squadify.presentation.errorMessage
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.msg_failed_to_load_player
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class PlayerDetailsViewModel(
    private val getPlayerDetailsUseCase: GetPlayerDetailsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<PlayerDetailsUiState>(PlayerDetailsUiState.Loading)
    val state: StateFlow<PlayerDetailsUiState> = _state.asStateFlow()

    fun loadPlayerDetails(playerId: String) {
        viewModelScope.launch {
            _state.update { PlayerDetailsUiState.Loading }
            try {
                val profile = getPlayerDetailsUseCase(playerId)
                _state.update { PlayerDetailsUiState.Idle(profile = profile) }
            } catch (e: Exception) {
                val message = errorMessage(e, Res.string.msg_failed_to_load_player)
                _state.update { PlayerDetailsUiState.Error(message) }
            }
        }
    }
}
