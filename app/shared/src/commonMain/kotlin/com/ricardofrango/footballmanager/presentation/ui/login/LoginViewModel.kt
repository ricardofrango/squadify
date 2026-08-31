package com.ricardofrango.footballmanager.presentation.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.footballmanager.domain.usecase.LoginUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class LoginViewModel(
    private val loginUseCase: LoginUseCase
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
                _navigationEvent.emit(LoginNavigationEvent.NavigateToDashboard)
            } catch (e: Exception) {
                _state.update { LoginUiState.Idle(error = e.message ?: "Unknown error") }
            }
        }
    }
}
