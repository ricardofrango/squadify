package com.ricardofrango.footballmanager.presentation.ui.agegroupdetails

import com.ricardofrango.footballmanager.model.AgeGroupDetails

sealed class AgeGroupDetailsUiState {
    data object Loading : AgeGroupDetailsUiState()
    data class Idle(val details: AgeGroupDetails) : AgeGroupDetailsUiState()
    data class Error(val message: String) : AgeGroupDetailsUiState()
}

/** Player-list ordering on the detail screen. Local screen state, not persisted. */
enum class PlayerSort { NAME, BIRTH_YEAR }
