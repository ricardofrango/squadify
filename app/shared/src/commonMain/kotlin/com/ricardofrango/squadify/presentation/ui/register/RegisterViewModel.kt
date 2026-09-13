package com.ricardofrango.squadify.presentation.ui.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.domain.usecase.RegisterUseCase
import com.ricardofrango.squadify.model.RegisterRequest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RegisterViewModel(
    private val registerUseCase: RegisterUseCase
) : ViewModel() {
    private val _state = MutableStateFlow<RegisterUiState>(RegisterUiState.Idle())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<RegisterNavigationEvent>()
    val navigationEvent: SharedFlow<RegisterNavigationEvent> = _navigationEvent.asSharedFlow()

    fun onRegisterClicked(
        firstName: String,
        lastName: String,
        email: String,
        phone: String,
        password: String,
        confirmPassword: String
    ) {
        if (password != confirmPassword) {
            _state.update { RegisterUiState.Idle(error = "Passwords do not match") }
            return
        }

        viewModelScope.launch {
            _state.update { RegisterUiState.Loading }
            try {
                registerUseCase(
                    RegisterRequest(
                        email = email,
                        password = password,
                        firstName = firstName,
                        lastName = lastName,
                        phone = phone.takeIf { it.isNotBlank() }
                    )
                )
                _state.update { RegisterUiState.Idle() }
                _navigationEvent.emit(RegisterNavigationEvent.NavigateBackToLogin)
            } catch (e: Exception) {
                _state.update { RegisterUiState.Idle(error = e.message ?: "Registration failed") }
            }
        }
    }
}
