package com.ricardofrango.squadify.presentation.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.domain.usecase.GetClubsUseCase
import com.ricardofrango.squadify.domain.usecase.LoginUseCase
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.msg_login_failed
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

class LoginViewModel(
    private val loginUseCase: LoginUseCase,
    private val getClubsUseCase: GetClubsUseCase
) : ViewModel() {
    private val _state = MutableStateFlow<LoginUiState>(LoginUiState.Idle())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<LoginNavigationEvent>()
    val navigationEvent: SharedFlow<LoginNavigationEvent> = _navigationEvent.asSharedFlow()

    fun onLoginClicked(email: String, password: String) {
        viewModelScope.launch {
            _state.update { LoginUiState.Loading }
            try {
                loginUseCase(email, password)
                // Checked up front so a single-club user goes straight to their club's details
                // instead of visiting Dashboard first -- see SplashViewModel's identical check.
                val onlyClub = try {
                    getClubsUseCase().singleOrNull()
                } catch (e: Exception) {
                    null
                }
                _navigationEvent.emit(
                    if (onlyClub != null) {
                        LoginNavigationEvent.NavigateToOnlyClub(onlyClub.id)
                    } else {
                        LoginNavigationEvent.NavigateToDashboard
                    }
                )
            } catch (e: Exception) {
                // Deliberately one generic message for every failure -- wrong credentials and a
                // suspended account both come back as the same 401 from the server (LoginUseCase
                // checks status only after the password verifies, precisely so this can't be used to
                // probe account existence/state), so there's nothing safe to differentiate on here.
                _state.update { LoginUiState.Idle(error = getString(Res.string.msg_login_failed)) }
            }
        }
    }
}
