package com.ricardofrango.footballmanager.presentation.ui.login

sealed class LoginUiState {
    data class Idle(val error: String? = null) : LoginUiState()
    data object Loading : LoginUiState()
}

sealed class LoginNavigationEvent {
    data object NavigateToDashboard : LoginNavigationEvent()
}
