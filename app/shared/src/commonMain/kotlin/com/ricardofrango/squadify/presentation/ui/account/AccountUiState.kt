package com.ricardofrango.squadify.presentation.ui.account

sealed class AccountUiState {
    data object Idle : AccountUiState()
}

sealed class AccountNavigationEvent {
    data object NavigateToLogin : AccountNavigationEvent()
}
