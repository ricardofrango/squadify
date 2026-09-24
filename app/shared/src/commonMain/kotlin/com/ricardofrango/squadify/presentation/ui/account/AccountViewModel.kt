package com.ricardofrango.squadify.presentation.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.domain.usecase.ChangePasswordUseCase
import com.ricardofrango.squadify.domain.usecase.GetCurrentUserUseCase
import com.ricardofrango.squadify.domain.usecase.LogoutUseCase
import com.ricardofrango.squadify.domain.usecase.UpdateProfileUseCase
import com.ricardofrango.squadify.model.ChangePasswordRequest
import com.ricardofrango.squadify.model.User
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class AccountViewModel(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val updateProfileUseCase: UpdateProfileUseCase,
    private val changePasswordUseCase: ChangePasswordUseCase,
    private val logoutUseCase: LogoutUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<AccountUiState>(AccountUiState.Loading)
    val state: StateFlow<AccountUiState> = _state.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<AccountNavigationEvent>()
    val navigationEvent: SharedFlow<AccountNavigationEvent> = _navigationEvent.asSharedFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = AccountUiState.Loading
            _state.value = try {
                idleFor(getCurrentUserUseCase())
            } catch (e: Exception) {
                AccountUiState.Error(e.message ?: "Couldn't load your account. Please try again.")
            }
        }
    }

    private fun idleFor(user: User): AccountUiState.Idle {
        val fields = ProfileFormFields(
            firstName = user.firstName,
            lastName = user.lastName,
            phone = user.phone.orEmpty()
        )
        return AccountUiState.Idle(
            email = user.email,
            memberSince = user.createdAt.toLocalDateTime(TimeZone.currentSystemDefault()).date,
            status = user.status,
            original = fields,
            form = fields
        )
    }

    private inline fun updateIdle(block: (AccountUiState.Idle) -> AccountUiState.Idle) {
        val current = _state.value as? AccountUiState.Idle ?: return
        _state.value = block(current)
    }

    fun updateProfileForm(form: ProfileFormFields) {
        updateIdle { it.copy(form = form, profileSubmitError = null) }
    }

    fun onSaveProfileClicked() {
        val idle = _state.value as? AccountUiState.Idle ?: return
        if (!idle.canSaveProfile) return
        viewModelScope.launch {
            updateIdle { it.copy(isSubmittingProfile = true, profileSubmitError = null) }
            try {
                val updated = updateProfileUseCase(idle.form.toRequest())
                updateIdle {
                    val fields = ProfileFormFields(updated.firstName, updated.lastName, updated.phone.orEmpty())
                    it.copy(original = fields, form = fields, isSubmittingProfile = false)
                }
            } catch (e: Exception) {
                updateIdle {
                    it.copy(
                        isSubmittingProfile = false,
                        profileSubmitError = e.message ?: "Couldn't save your changes. Please try again."
                    )
                }
            }
        }
    }

    fun updatePasswordForm(form: PasswordFormFields) {
        updateIdle { it.copy(passwordForm = form, passwordSubmitError = null, passwordChanged = false) }
    }

    fun onChangePasswordClicked() {
        val idle = _state.value as? AccountUiState.Idle ?: return
        if (!idle.canChangePassword) return
        viewModelScope.launch {
            updateIdle { it.copy(isSubmittingPassword = true, passwordSubmitError = null) }
            try {
                changePasswordUseCase(
                    ChangePasswordRequest(
                        currentPassword = idle.passwordForm.currentPassword,
                        newPassword = idle.passwordForm.newPassword
                    )
                )
                updateIdle {
                    it.copy(
                        isSubmittingPassword = false,
                        passwordForm = PasswordFormFields(),
                        passwordChanged = true
                    )
                }
            } catch (e: Exception) {
                updateIdle {
                    it.copy(
                        isSubmittingPassword = false,
                        passwordSubmitError = e.message ?: "Couldn't change your password. Please try again."
                    )
                }
            }
        }
    }

    fun onLogoutClicked() {
        viewModelScope.launch {
            logoutUseCase()
            _navigationEvent.emit(AccountNavigationEvent.NavigateToLogin)
        }
    }
}
