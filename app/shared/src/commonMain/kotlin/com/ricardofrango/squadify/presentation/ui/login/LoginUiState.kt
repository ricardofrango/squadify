package com.ricardofrango.squadify.presentation.ui.login

import com.ricardofrango.squadify.presentation.navigation.Screen

sealed class LoginUiState {
    data class Idle(val error: String? = null) : LoginUiState()
    data object Loading : LoginUiState()
}

sealed class LoginNavigationEvent {
    // The app's home once signed in (SignedInHome).
    data class NavigateToHome(val screen: Screen) : LoginNavigationEvent()
}
