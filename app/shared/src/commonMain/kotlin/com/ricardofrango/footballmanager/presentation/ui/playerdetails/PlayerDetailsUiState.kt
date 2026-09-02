package com.ricardofrango.footballmanager.presentation.ui.playerdetails

import com.ricardofrango.footballmanager.model.Player

sealed class PlayerDetailsUiState {
    data class Idle(val player: Player? = null, val error: String? = null) : PlayerDetailsUiState()
    data object Loading : PlayerDetailsUiState()
}
