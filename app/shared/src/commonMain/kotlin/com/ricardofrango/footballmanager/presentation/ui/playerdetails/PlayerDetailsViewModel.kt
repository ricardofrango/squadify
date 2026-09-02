package com.ricardofrango.footballmanager.presentation.ui.playerdetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.footballmanager.domain.usecase.GetPlayerDetailsUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class PlayerDetailsViewModel(
    private val getPlayerDetailsUseCase: GetPlayerDetailsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<PlayerDetailsUiState>(PlayerDetailsUiState.Idle())
    val state: StateFlow<PlayerDetailsUiState> = _state.asStateFlow()

    fun loadPlayerDetails(playerId: String) {
        viewModelScope.launch {
            _state.update { PlayerDetailsUiState.Loading }
            try {
                val player = getPlayerDetailsUseCase(playerId)
                _state.update { PlayerDetailsUiState.Idle(player = player) }
            } catch (e: Exception) {
                _state.update { PlayerDetailsUiState.Idle(error = e.message ?: "Failed to load player details") }
            }
        }
    }
}
