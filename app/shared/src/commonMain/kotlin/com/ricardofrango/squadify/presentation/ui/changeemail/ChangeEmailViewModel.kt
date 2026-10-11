package com.ricardofrango.squadify.presentation.ui.changeemail

import com.ricardofrango.squadify.presentation.ui.components.isWrongCurrentPassword
import com.ricardofrango.squadify.presentation.ui.components.isSignedOutForWrongPasswords
import com.ricardofrango.squadify.domain.usecase.LogoutUseCase
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.data.remote.ApiException
import com.ricardofrango.squadify.domain.usecase.ConfirmEmailChangeUseCase
import com.ricardofrango.squadify.domain.usecase.RequestEmailChangeUseCase
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
import squadify.app.shared.generated.resources.msg_change_email_failed
import squadify.app.shared.generated.resources.msg_change_email_same
import squadify.app.shared.generated.resources.msg_change_email_taken
import squadify.app.shared.generated.resources.msg_change_email_wrong_password
import squadify.app.shared.generated.resources.msg_enter_email
import squadify.app.shared.generated.resources.msg_reset_code_format
import squadify.app.shared.generated.resources.msg_reset_code_invalid

enum class ChangeEmailStep { EMAIL, CODE }

data class ChangeEmailUiState(
    val step: ChangeEmailStep = ChangeEmailStep.EMAIL,
    // The address the code was sent to (shown on the code step).
    val newEmail: String = "",
    val submitting: Boolean = false,
    val error: String? = null,
    // Third wrong current password: the server signed the user out; the screen shows SignedOutAlert.
    val signedOut: Boolean = false,
    // Seconds until "Send a new code" is enabled again; matches the server's 60-second cooldown.
    val resendInSeconds: Int = 0
)

private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

/** Step-1 problem to show before calling the server, or null when it's fine to submit. */
internal fun changeEmailFormError(newEmail: String, currentEmail: String?): StringResource? = when {
    !EMAIL.matches(newEmail.trim()) -> Res.string.msg_enter_email
    newEmail.trim() == currentEmail -> Res.string.msg_change_email_same
    else -> null
}

/**
 * Change email, from Account. Step 1: the new address and the current password; a 6-digit code goes
 * to the new address. Step 2: the code. On success it emits the new address, for Account to show.
 */
class ChangeEmailViewModel(
    private val requestEmailChange: RequestEmailChangeUseCase,
    private val confirmEmailChange: ConfirmEmailChangeUseCase,
    private val logoutUseCase: LogoutUseCase
) : ViewModel() {
    private val _state = MutableStateFlow(ChangeEmailUiState())
    val state: StateFlow<ChangeEmailUiState> = _state.asStateFlow()

    private val _done = MutableSharedFlow<String>()
    val done: SharedFlow<String> = _done.asSharedFlow()

    private var countdown: Job? = null
    // Kept for "Send a new code", which repeats the request without asking for the password again.
    private var password: String = ""

    fun clearError() {
        if (_state.value.error != null) _state.update { it.copy(error = null) }
    }

    fun sendCode(newEmail: String, currentPassword: String, currentEmail: String?) {
        viewModelScope.launch {
            changeEmailFormError(newEmail, currentEmail)?.let { problem ->
                _state.update { it.copy(error = getString(problem)) }
                return@launch
            }
            _state.update { it.copy(submitting = true, error = null) }
            try {
                requestEmailChange(newEmail, currentPassword)
                password = currentPassword
                _state.update { it.copy(step = ChangeEmailStep.CODE, newEmail = newEmail.trim(), submitting = false) }
                startCountdown()
            } catch (e: Exception) {
                if (e.isSignedOutForWrongPasswords()) {
                    _state.update { it.copy(submitting = false, signedOut = true) }
                    return@launch
                }
                val message = when {
                    e.isWrongCurrentPassword() -> Res.string.msg_change_email_wrong_password
                    else -> when ((e as? ApiException)?.statusCode) {
                    HttpStatusCode.Conflict.value -> Res.string.msg_change_email_taken
                    HttpStatusCode.BadRequest.value -> Res.string.msg_change_email_same
                    else -> Res.string.msg_change_email_failed
                    }
                }
                _state.update { it.copy(submitting = false, error = getString(message)) }
            }
        }
    }

    // Separate, so Done/OK on the alert always reaches Sign in even if the event is emitted late.
    private val _signOutDone = MutableSharedFlow<Unit>()
    val signOutDone: SharedFlow<Unit> = _signOutDone.asSharedFlow()

    /** OK on SignedOutAlert: clear the local session and go to Sign in. */
    fun finishSignOut() {
        viewModelScope.launch {
            logoutUseCase()
            _signOutDone.emit(Unit)
        }
    }

    fun resendCode() {
        val s = _state.value
        if (s.resendInSeconds > 0) return
        sendCode(s.newEmail, password, currentEmail = null)
    }

    fun useDifferentEmail() {
        countdown?.cancel()
        _state.update { ChangeEmailUiState(newEmail = it.newEmail) }
    }

    fun confirm(code: String) {
        viewModelScope.launch {
            val digits = code.filter { !it.isWhitespace() }
            if (!Regex("\\d{6}").matches(digits)) {
                _state.update { it.copy(error = getString(Res.string.msg_reset_code_format)) }
                return@launch
            }
            _state.update { it.copy(submitting = true, error = null) }
            try {
                val user = confirmEmailChange(digits)
                _done.emit(user.email)
            } catch (e: Exception) {
                val message = when ((e as? ApiException)?.statusCode) {
                    HttpStatusCode.BadRequest.value -> Res.string.msg_reset_code_invalid
                    HttpStatusCode.Conflict.value -> Res.string.msg_change_email_taken
                    else -> Res.string.msg_change_email_failed
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
