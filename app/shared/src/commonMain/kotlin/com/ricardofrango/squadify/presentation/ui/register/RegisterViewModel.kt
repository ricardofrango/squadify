package com.ricardofrango.squadify.presentation.ui.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.data.remote.ApiException
import com.ricardofrango.squadify.domain.usecase.RegisterUseCase
import com.ricardofrango.squadify.model.RegisterRequest
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.msg_email_already_registered
import squadify.app.shared.generated.resources.msg_passwords_do_not_match
import squadify.app.shared.generated.resources.msg_registration_failed
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

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
        viewModelScope.launch {
            if (password != confirmPassword) {
                _state.update { RegisterUiState.Idle(error = getString(Res.string.msg_passwords_do_not_match)) }
                return@launch
            }

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
            } catch (e: ApiException) {
                // 409 = ConflictException("User with this email already exists") -- worth naming
                // specifically since it's actionable (go log in instead) and doesn't leak anything
                // sensitive, unlike login's deliberately generic failure message.
                val message = if (e.statusCode == 409) {
                    getString(Res.string.msg_email_already_registered)
                } else {
                    getString(Res.string.msg_registration_failed)
                }
                _state.update { RegisterUiState.Idle(error = message) }
            } catch (e: Exception) {
                _state.update { RegisterUiState.Idle(error = getString(Res.string.msg_registration_failed)) }
            }
        }
    }
}
