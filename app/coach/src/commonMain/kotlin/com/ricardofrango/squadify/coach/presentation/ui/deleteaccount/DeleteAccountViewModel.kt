package com.ricardofrango.squadify.coach.presentation.ui.deleteaccount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.coach.domain.DeleteAccountUseCase
import com.ricardofrango.squadify.coach.domain.GetAccountDeletionSummaryUseCase
import com.ricardofrango.squadify.domain.usecase.LogoutUseCase
import com.ricardofrango.squadify.model.AccountDeletionSummary
import com.ricardofrango.squadify.presentation.ui.components.isSignedOutForWrongPasswords
import com.ricardofrango.squadify.presentation.ui.components.isWrongCurrentPassword
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class DeleteAccountUiState {
    data object Loading : DeleteAccountUiState()
    data object Failed : DeleteAccountUiState()

    data class Idle(
        val summary: AccountDeletionSummary,
        val password: String = "",
        val isDeleting: Boolean = false,
        val error: DeleteAccountError? = null,
        // Third wrong password: the server signed them out; SignedOutAlert takes them to Sign in.
        val signedOut: Boolean = false
    ) : DeleteAccountUiState() {
        val canDelete: Boolean get() = password.isNotEmpty() && !isDeleting
    }
}

enum class DeleteAccountError { WRONG_PASSWORD, FAILED }

class DeleteAccountViewModel(
    private val getSummary: GetAccountDeletionSummaryUseCase,
    private val deleteAccount: DeleteAccountUseCase,
    private val logoutUseCase: LogoutUseCase
) : ViewModel() {
    private val _state = MutableStateFlow<DeleteAccountUiState>(DeleteAccountUiState.Loading)
    val state: StateFlow<DeleteAccountUiState> = _state.asStateFlow()

    // Deleted (or signed out): the local session is cleared, then Sign in.
    private val _done = MutableSharedFlow<Unit>()
    val done: SharedFlow<Unit> = _done.asSharedFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = DeleteAccountUiState.Loading
            _state.value = try {
                DeleteAccountUiState.Idle(getSummary())
            } catch (e: Exception) {
                DeleteAccountUiState.Failed
            }
        }
    }

    fun onPasswordChanged(password: String) = _state.update {
        (it as? DeleteAccountUiState.Idle)?.copy(password = password, error = null) ?: it
    }

    /** After the confirmation: deletes the account, then signs out on this device. */
    fun onDeleteConfirmed() {
        val idle = _state.value as? DeleteAccountUiState.Idle ?: return
        if (!idle.canDelete) return
        viewModelScope.launch {
            _state.value = idle.copy(isDeleting = true, error = null)
            try {
                deleteAccount(idle.password)
                finishSignOut()
            } catch (e: Exception) {
                _state.update { s ->
                    (s as? DeleteAccountUiState.Idle)?.let {
                        when {
                            e.isSignedOutForWrongPasswords() -> it.copy(isDeleting = false, signedOut = true)
                            e.isWrongCurrentPassword() -> it.copy(isDeleting = false, error = DeleteAccountError.WRONG_PASSWORD)
                            else -> it.copy(isDeleting = false, error = DeleteAccountError.FAILED)
                        }
                    } ?: s
                }
            }
        }
    }

    /** Clears the local session (after the deletion, or OK on SignedOutAlert), then Sign in. */
    fun finishSignOut() {
        viewModelScope.launch {
            logoutUseCase()
            _done.emit(Unit)
        }
    }
}
