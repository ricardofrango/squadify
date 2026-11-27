package com.sportssoftw.squadcall.presentation.ui.account

import com.sportssoftw.squadcall.data.cache.ForceRefresh
import com.sportssoftw.squadcall.presentation.refresh.RefreshFlag
import kotlinx.coroutines.CancellationException
import com.sportssoftw.squadcall.domain.config.AppFeatures
import squadcall.app.shared.generated.resources.msg_password_change_wrong_password
import org.jetbrains.compose.resources.getString
import com.sportssoftw.squadcall.presentation.ui.components.isWrongCurrentPassword
import com.sportssoftw.squadcall.presentation.ui.components.isSignedOutForWrongPasswords
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportssoftw.squadcall.domain.usecase.ChangePasswordUseCase
import com.sportssoftw.squadcall.domain.usecase.DeleteUserAvatarUseCase
import com.sportssoftw.squadcall.domain.usecase.GetCurrentUserUseCase
import com.sportssoftw.squadcall.domain.usecase.LogoutUseCase
import com.sportssoftw.squadcall.domain.usecase.UpdateProfileUseCase
import com.sportssoftw.squadcall.domain.usecase.UploadUserAvatarUseCase
import com.sportssoftw.squadcall.presentation.PhotoChanger
import com.sportssoftw.squadcall.presentation.ui.components.PhotoState
import com.sportssoftw.squadcall.model.ChangePasswordRequest
import com.sportssoftw.squadcall.model.User
import com.sportssoftw.squadcall.presentation.errorMessage
import squadcall.app.shared.generated.resources.Res
import squadcall.app.shared.generated.resources.msg_failed_to_load_account
import squadcall.app.shared.generated.resources.msg_password_change_failed
import squadcall.app.shared.generated.resources.msg_profile_save_failed
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

    // Delete account: the coach app only, for now.
    val offersAccountDeletion: Boolean = features.accountDeletion

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

    private val refreshFlag = RefreshFlag()

    /** Up while a pull-to-refresh reads the account again. */
    val isRefreshing: StateFlow<Boolean> = refreshFlag.running

    /**
     * A pull-to-refresh: the server's account, not the cache. What the user has typed and not saved is kept; if the
     * read fails, the screen stays as it is.
     */
    fun refreshContent() {
        viewModelScope.launch(ForceRefresh) { refreshFlag.during { reloadKeepingEdits() } }
    }

    private suspend fun reloadKeepingEdits() {
        val fresh = try {
            idleFor(getCurrentUserUseCase())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return
        }
        val current = _state.value as? AccountUiState.Idle
        _state.value = if (current == null) {
            fresh
        } else {
            current.copy(
                email = fresh.email,
                memberSince = fresh.memberSince,
                status = fresh.status,
                original = fresh.original,
                form = if (current.form == current.original) fresh.form else current.form
            )
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
