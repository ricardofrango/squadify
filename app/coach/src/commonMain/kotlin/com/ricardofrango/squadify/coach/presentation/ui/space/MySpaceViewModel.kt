package com.ricardofrango.squadify.coach.presentation.ui.space

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.coach.domain.DeleteSpaceUseCase
import com.ricardofrango.squadify.coach.domain.GetSpacesUseCase
import com.ricardofrango.squadify.domain.usecase.DeleteClubLogoUseCase
import com.ricardofrango.squadify.domain.usecase.UploadClubLogoUseCase
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.model.SpacePlan
import com.ricardofrango.squadify.presentation.ui.components.isSignedOutForWrongPasswords
import com.ricardofrango.squadify.presentation.ui.components.isWrongCurrentPassword
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why the logo didn't change, shown under it. */
enum class LogoProblem { TOO_BIG, UPLOAD_FAILED, REMOVE_FAILED }

/** Why the space wasn't deleted, shown in its dialog. */
enum class DeleteSpaceError { WRONG_PASSWORD, FAILED }

/** The confirmation for deleting the space: the owner's password, while it's being deleted, and why it failed. */
data class DeleteSpaceDialog(
    val password: String = "",
    val isDeleting: Boolean = false,
    val error: DeleteSpaceError? = null
) {
    val canDelete: Boolean get() = password.isNotEmpty() && !isDeleting
}

sealed class MySpaceUiState {
    data object Loading : MySpaceUiState()
    data object Failed : MySpaceUiState()

    /**
     * The owner's space, with its [plan] and [ageGroupsThisSeason] for the Plan card; [isUpdatingLogo]
     * while a logo is being saved or removed.
     */
    data class Loaded(
        val space: Club,
        val plan: SpacePlan,
        val ageGroupsThisSeason: Int = 0,
        // All the space's age groups, every season: what deleting it takes with it.
        val ageGroupCount: Int = 0,
        val isUpdatingLogo: Boolean = false,
        val logoProblem: LogoProblem? = null
    ) : MySpaceUiState()
}

/**
 * The owner's own space (the ☰ menu's My space): its plan, and its logo, saved straight away as in the clubs app's
 * Club settings. A space is a club on the coach server, so the logo is the club's (`/clubs/{id}/logo`).
 */
class MySpaceViewModel(
    private val getSpacesUseCase: GetSpacesUseCase,
    private val uploadClubLogoUseCase: UploadClubLogoUseCase,
    private val deleteClubLogoUseCase: DeleteClubLogoUseCase,
    private val deleteSpaceUseCase: DeleteSpaceUseCase
) : ViewModel() {
    private val _state = MutableStateFlow<MySpaceUiState>(MySpaceUiState.Loading)
    val state: StateFlow<MySpaceUiState> = _state.asStateFlow()

    // The delete-space confirmation, null while it's closed.
    private val _deleteDialog = MutableStateFlow<DeleteSpaceDialog?>(null)
    val deleteDialog: StateFlow<DeleteSpaceDialog?> = _deleteDialog.asStateFlow()

    // The space is gone: Home reloads and the stack goes back to it.
    private val _spaceDeleted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val spaceDeleted: SharedFlow<Unit> = _spaceDeleted.asSharedFlow()

    /** Each time the logo changes, so Home reloads its rows. */
    private val _logoChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val logoChanged: SharedFlow<Unit> = _logoChanged.asSharedFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = MySpaceUiState.Loading
            _state.value = try {
                getSpacesUseCase().firstOrNull { it.isOwner }?.let { MySpaceUiState.Loaded(it.space, it.plan, it.ageGroupsThisSeason, it.ageGroups.size) } ?: MySpaceUiState.Failed
            } catch (e: Exception) {
                MySpaceUiState.Failed
            }
        }
    }

    /** Uploads [bytes] as the logo; the server checks and resizes it. Too big for the server, it's refused here. */
    fun onLogoPicked(bytes: ByteArray) {
        if (bytes.size > MAX_LOGO_BYTES) {
            updateLoaded { it.copy(logoProblem = LogoProblem.TOO_BIG) }
            return
        }
        changeLogo(LogoProblem.UPLOAD_FAILED) { uploadClubLogoUseCase(it, bytes) }
    }

    fun onRemoveLogo() = changeLogo(LogoProblem.REMOVE_FAILED) { deleteClubLogoUseCase(it); null }

    private fun changeLogo(failure: LogoProblem, action: suspend (spaceId: String) -> String?) {
        val loaded = _state.value as? MySpaceUiState.Loaded ?: return
        if (loaded.isUpdatingLogo) return
        viewModelScope.launch {
            updateLoaded { it.copy(isUpdatingLogo = true, logoProblem = null) }
            try {
                val url = action(loaded.space.id)
                _logoChanged.tryEmit(Unit)
                updateLoaded { it.copy(space = it.space.copy(logoUrl = url), isUpdatingLogo = false) }
            } catch (e: Exception) {
                updateLoaded { it.copy(isUpdatingLogo = false, logoProblem = failure) }
            }
        }
    }

    fun onDeleteSpaceOpened() {
        _deleteDialog.value = DeleteSpaceDialog()
    }

    fun onDeleteSpaceDismissed() {
        if (_deleteDialog.value?.isDeleting != true) _deleteDialog.value = null
    }

    fun onDeletePasswordChanged(password: String) = _deleteDialog.update { it?.copy(password = password, error = null) }

    /** Deletes the space after the owner's password; on success the dialog closes and [spaceDeleted] fires. */
    fun onDeleteSpaceConfirmed() {
        val loaded = _state.value as? MySpaceUiState.Loaded ?: return
        val dialog = _deleteDialog.value ?: return
        if (!dialog.canDelete) return
        viewModelScope.launch {
            _deleteDialog.update { it?.copy(isDeleting = true, error = null) }
            try {
                deleteSpaceUseCase(loaded.space.id, dialog.password)
                _deleteDialog.value = null
                _spaceDeleted.tryEmit(Unit)
            } catch (e: Exception) {
                val error = if (e.isWrongCurrentPassword() || e.isSignedOutForWrongPasswords()) DeleteSpaceError.WRONG_PASSWORD else DeleteSpaceError.FAILED
                _deleteDialog.update { it?.copy(isDeleting = false, error = error) }
            }
        }
    }

    private fun updateLoaded(block: (MySpaceUiState.Loaded) -> MySpaceUiState.Loaded) =
        _state.update { (it as? MySpaceUiState.Loaded)?.let(block) ?: it }
}

// The server's limit (LogoImage.MAX_UPLOAD_BYTES): checked here too, so a big image isn't sent for nothing.
internal const val MAX_LOGO_BYTES = 5 * 1024 * 1024
