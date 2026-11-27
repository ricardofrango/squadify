package com.sportssoftw.squadcall.presentation.ui.login

import com.sportssoftw.squadcall.presentation.navigation.Screen

sealed class LoginUiState {
    data class Idle(val error: String? = null) : LoginUiState()
    data object Loading : LoginUiState()
}

sealed class LoginNavigationEvent {
    // The app's home once signed in (SignedInHome).
    data class NavigateToHome(val screen: Screen) : LoginNavigationEvent()
}
