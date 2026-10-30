package com.ricardofrango.squadify.coach.presentation.ui.spacesetup

data class SpaceSetupUiState(
    val form: SpaceSetupForm = SpaceSetupForm(),
    val isSubmitting: Boolean = false,
    val failed: Boolean = false
)

sealed class SpaceSetupNavigationEvent {
    // Set up (or it already was, on another device): back to the home, which reloads.
    data object NavigateToHome : SpaceSetupNavigationEvent()
}
