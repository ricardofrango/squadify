package com.ricardofrango.footballmanager.presentation.ui.register

sealed class RegisterUiState {
    data class Idle(val error: String? = null) : RegisterUiState()
    data object Loading : RegisterUiState()
}

sealed class RegisterNavigationEvent {
    data object NavigateToDashboard : RegisterNavigationEvent()
}
