package com.ricardofrango.clubmanager.presentation.ui.playerdetails

import com.ricardofrango.clubmanager.model.Player

sealed class PlayerDetailsUiState {
    data class Idle(val player: Player? = null, val error: String? = null) : PlayerDetailsUiState()
    data object Loading : PlayerDetailsUiState()
}
