package com.ricardofrango.clubmanager.presentation.ui.playerdetails

import com.ricardofrango.clubmanager.model.PlayerProfile

sealed class PlayerDetailsUiState {
    data object Loading : PlayerDetailsUiState()
    data class Error(val message: String) : PlayerDetailsUiState()
    data class Idle(val profile: PlayerProfile) : PlayerDetailsUiState()
}
