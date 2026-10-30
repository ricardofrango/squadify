package com.ricardofrango.squadify.presentation.ui.account

import com.ricardofrango.squadify.domain.config.AppFeatures
import squadify.app.shared.generated.resources.msg_password_change_wrong_password
import org.jetbrains.compose.resources.getString
import com.ricardofrango.squadify.presentation.ui.components.isWrongCurrentPassword
import com.ricardofrango.squadify.presentation.ui.components.isSignedOutForWrongPasswords
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.domain.usecase.ChangePasswordUseCase
import com.ricardofrango.squadify.domain.usecase.DeleteUserAvatarUseCase
import com.ricardofrango.squadify.domain.usecase.GetCurrentUserUseCase
import com.ricardofrango.squadify.domain.usecase.LogoutUseCase
import com.ricardofrango.squadify.domain.usecase.UpdateProfileUseCase
import com.ricardofrango.squadify.domain.usecase.UploadUserAvatarUseCase
import com.ricardofrango.squadify.presentation.PhotoChanger
import com.ricardofrango.squadify.presentation.ui.components.PhotoState
import com.ricardofrango.squadify.model.ChangePasswordRequest
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.presentation.errorMessage
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.msg_failed_to_load_account
import squadify.app.shared.generated.resources.msg_password_change_failed
import squadify.app.shared.generated.resources.msg_profile_save_failed
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class AccountViewModel(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val updateProfileUseCase: UpdateProfileUseCase,
    private val changePasswordUseCase: ChangePasswordUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val uploadUserAvatarUseCase: UploadUserAvatarUseCase,
    private val deleteUserAvatarUseCase: DeleteUserAvatarUseCase,
    features: AppFeatures
) : ViewModel() {

    // The coach app has no photos until its server's image storage: no photo or photo actions.
    val showsPhoto: Boolean = features.photos

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
                AccountUiState.Error(errorMessage(e, Res.string.msg_failed_to_load_account))
            }
        }
    }

    /** Back from Change email: show the new address (already saved on the server) with a confirmation. */
    fun onEmailChanged(email: String) {
        val idle = _state.value as? AccountUiState.Idle ?: return
        if (idle.email != email) _state.value = idle.copy(email = email, emailChanged = true)
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
            form = fields,
            photo = PhotoState(url = user.avatarUrl)
        )
    }

    private val photoChanger = PhotoChanger(
        scope = viewModelScope,
        read = { (_state.value as? AccountUiState.Idle)?.photo },
        write = { change -> updateIdle { it.copy(photo = change(it.photo)) } }
    )

    fun onPhotoPicked(bytes: ByteArray) = photoChanger.upload(bytes, { uploadUserAvatarUseCase(it) })

    fun onRemovePhoto() = photoChanger.remove({ deleteUserAvatarUseCase() })

    private fun updateIdle(block: (AccountUiState.Idle) -> AccountUiState.Idle) {
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
                val message = errorMessage(e, Res.string.msg_profile_save_failed)
                updateIdle { it.copy(isSubmittingProfile = false, profileSubmitError = message) }
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
                if (e.isSignedOutForWrongPasswords()) {
                    updateIdle { it.copy(isSubmittingPassword = false, signedOut = true) }
                    return@launch
                }
                val message = if (e.isWrongCurrentPassword()) {
                    getString(Res.string.msg_password_change_wrong_password)
                } else {
                    errorMessage(e, Res.string.msg_password_change_failed)
                }
                updateIdle { it.copy(isSubmittingPassword = false, passwordSubmitError = message) }
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
