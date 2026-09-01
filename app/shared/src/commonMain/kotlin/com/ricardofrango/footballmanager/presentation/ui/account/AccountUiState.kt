package com.ricardofrango.footballmanager.presentation.ui.account

sealed class AccountUiState {
    data object Idle : AccountUiState()
}

sealed class AccountNavigationEvent {
    data object NavigateToLogin : AccountNavigationEvent()
}
