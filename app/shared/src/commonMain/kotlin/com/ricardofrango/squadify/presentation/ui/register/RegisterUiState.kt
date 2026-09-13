package com.ricardofrango.squadify.presentation.ui.register

sealed class RegisterUiState {
    data class Idle(val error: String? = null) : RegisterUiState()
    data object Loading : RegisterUiState()
}

sealed class RegisterNavigationEvent {
    data object NavigateBackToLogin : RegisterNavigationEvent()
    data object NavigateToDashboard : RegisterNavigationEvent()
}
