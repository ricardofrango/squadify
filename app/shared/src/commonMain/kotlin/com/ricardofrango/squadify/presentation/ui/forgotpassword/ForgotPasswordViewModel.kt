package com.ricardofrango.squadify.presentation.ui.forgotpassword

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.data.remote.ApiException
import com.ricardofrango.squadify.domain.usecase.RequestPasswordResetUseCase
import com.ricardofrango.squadify.domain.usecase.ResetPasswordUseCase
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.msg_enter_email
import squadify.app.shared.generated.resources.msg_password_min_length
import squadify.app.shared.generated.resources.msg_passwords_do_not_match
import squadify.app.shared.generated.resources.msg_reset_code_format
import squadify.app.shared.generated.resources.msg_reset_code_invalid
import squadify.app.shared.generated.resources.msg_reset_failed
import squadify.app.shared.generated.resources.msg_reset_no_account
import squadify.app.shared.generated.resources.msg_reset_request_failed

enum class ForgotPasswordStep { EMAIL, CODE }

data class ForgotPasswordUiState(
    val step: ForgotPasswordStep = ForgotPasswordStep.EMAIL,
    // The email the code was sent to (shown on the code step).
    val email: String = "",
    val submitting: Boolean = false,
    val error: String? = null,
    // Seconds until "Send a new code" is enabled again; matches the server's 60-second cooldown.
    val resendInSeconds: Int = 0
)

/** The code-step form problem to show before calling the server, or null when it's fine to submit. */
internal fun resetFormError(code: String, newPassword: String, confirmPassword: String): StringResource? = when {
    !Regex("\\d{6}").matches(code.filter { !it.isWhitespace() }) -> Res.string.msg_reset_code_format
    newPassword.length < 8 -> Res.string.msg_password_min_length
    newPassword != confirmPassword -> Res.string.msg_passwords_do_not_match
    else -> null
}

/**
 * Forgot password: step 1 asks for the email and has a 6-digit code sent; step 2 takes the code and
 * the new password. An email with no account stays on step 1 with "There's no account with this email".
 */
class ForgotPasswordViewModel(
    private val requestPasswordReset: RequestPasswordResetUseCase,
    private val resetPassword: ResetPasswordUseCase
) : ViewModel() {
    private val _state = MutableStateFlow(ForgotPasswordUiState())
    val state: StateFlow<ForgotPasswordUiState> = _state.asStateFlow()

    // Emits the email once the password has been changed, for Login to prefill.
    private val _done = MutableSharedFlow<String>()
    val done: SharedFlow<String> = _done.asSharedFlow()

    private var countdown: Job? = null

    fun sendCode(email: String) {
        val trimmed = email.trim()
        if (trimmed.isEmpty() || '@' !in trimmed) {
            viewModelScope.launch { _state.update { it.copy(error = getString(Res.string.msg_enter_email)) } }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null) }
            try {
                requestPasswordReset(trimmed)
                _state.update { it.copy(step = ForgotPasswordStep.CODE, email = trimmed, submitting = false) }
                startCountdown()
            } catch (e: Exception) {
                val message = if (e is ApiException && e.statusCode == HttpStatusCode.NotFound.value) {
                    Res.string.msg_reset_no_account
                } else {
                    Res.string.msg_reset_request_failed
                }
                // A resend from the code step that fails stays on the code step, with the error there.
                _state.update { it.copy(submitting = false, error = getString(message)) }
            }
        }
    }

    /** Editing a field clears the error shown for it. */
    fun clearError() {
        if (_state.value.error != null) _state.update { it.copy(error = null) }
    }

    fun resendCode() {
        if (_state.value.resendInSeconds > 0) return
        sendCode(_state.value.email)
    }

    fun useDifferentEmail() {
        countdown?.cancel()
        _state.update { ForgotPasswordUiState(email = it.email) }
    }

    fun setNewPassword(code: String, newPassword: String, confirmPassword: String) {
        viewModelScope.launch {
            resetFormError(code, newPassword, confirmPassword)?.let { problem ->
                _state.update { it.copy(error = getString(problem)) }
                return@launch
            }
            _state.update { it.copy(submitting = true, error = null) }
            try {
                resetPassword(_state.value.email, code.filter { !it.isWhitespace() }, newPassword)
                _done.emit(_state.value.email)
            } catch (e: Exception) {
                val message = if (e is ApiException && e.statusCode == HttpStatusCode.BadRequest.value) {
                    Res.string.msg_reset_code_invalid
                } else {
                    Res.string.msg_reset_failed
                }
                _state.update { it.copy(submitting = false, error = getString(message)) }
            }
        }
    }

    private fun startCountdown() {
        countdown?.cancel()
        countdown = viewModelScope.launch {
            for (left in RESEND_COOLDOWN_SECONDS downTo 0) {
                _state.update { it.copy(resendInSeconds = left) }
                if (left > 0) delay(1_000)
            }
        }
    }

    private companion object {
        const val RESEND_COOLDOWN_SECONDS = 60
    }
}
