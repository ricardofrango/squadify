package com.ricardofrango.squadify.presentation.ui.login

sealed class LoginUiState {
    data class Idle(val error: String? = null) : LoginUiState()
    data object Loading : LoginUiState()
}

sealed class LoginNavigationEvent {
    data object NavigateToDashboard : LoginNavigationEvent()
    // See SplashViewModel's identical check -- a single-club user goes straight to their club's
    // details instead of Dashboard, whose clubs list has nothing to offer them.
    data class NavigateToOnlyClub(val clubId: String) : LoginNavigationEvent()
}
