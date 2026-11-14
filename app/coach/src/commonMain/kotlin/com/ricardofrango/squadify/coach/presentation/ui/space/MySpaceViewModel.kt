package com.ricardofrango.squadify.coach.presentation.ui.space

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.coach.domain.DeleteSpaceUseCase
import com.ricardofrango.squadify.coach.domain.GetSpacesUseCase
import com.ricardofrango.squadify.coach.domain.LeaveSpaceUseCase
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

/** Why the space wasn't left, shown in its dialog. */
enum class LeaveSpaceError { WRONG_PASSWORD, FAILED }

/** The confirmation for leaving a space as staff: their password, while it's being done, and why it failed. */
data class LeaveSpaceDialog(
    val password: String = "",
    val isLeaving: Boolean = false,
    val error: LeaveSpaceError? = null
) {
    val canLeave: Boolean get() = password.isNotEmpty() && !isLeaving
}

sealed class MySpaceUiState {
    data object Loading : MySpaceUiState()
    data object Failed : MySpaceUiState()

    /**
     * The space, with its [plan] and [ageGroupsThisSeason] for the Plan card, and [ageGroupCount]: every age group
     * it has, what deleting it takes with it. [isOwner]: the owner deletes it; staff leave it, and see no plan.
     */
    data class Loaded(
        val space: Club,
        val plan: SpacePlan,
        val ageGroupsThisSeason: Int = 0,
        val ageGroupCount: Int = 0,
        val isOwner: Boolean = true
    ) : MySpaceUiState()
}

/**
 * A space's edit screen (My club or team): its name and sport, and for the owner its plan and deleting it; for staff,
 * leaving it. [open] names the space (null: the owner's own).
 */
class MySpaceViewModel(
    private val getSpacesUseCase: GetSpacesUseCase,
    private val deleteSpaceUseCase: DeleteSpaceUseCase,
    private val leaveSpaceUseCase: LeaveSpaceUseCase
) : ViewModel() {
    private val _state = MutableStateFlow<MySpaceUiState>(MySpaceUiState.Loading)
    val state: StateFlow<MySpaceUiState> = _state.asStateFlow()

    // The space this screen is about: null is the owner's own.
    private var requestedSpaceId: String? = null

    // The delete-space confirmation, null while it's closed.
    private val _deleteDialog = MutableStateFlow<DeleteSpaceDialog?>(null)
    val deleteDialog: StateFlow<DeleteSpaceDialog?> = _deleteDialog.asStateFlow()

    // The space is gone: Home reloads and the stack goes back to it.
    private val _spaceDeleted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val spaceDeleted: SharedFlow<Unit> = _spaceDeleted.asSharedFlow()

    // The leave-space confirmation, null while it's closed.
    private val _leaveDialog = MutableStateFlow<LeaveSpaceDialog?>(null)
    val leaveDialog: StateFlow<LeaveSpaceDialog?> = _leaveDialog.asStateFlow()

    // Left: back to Home, which lists what's left.
    private val _spaceLeft = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val spaceLeft: SharedFlow<Unit> = _spaceLeft.asSharedFlow()

    /** Shows [spaceId] (null: the owner's own space). */
    fun open(spaceId: String?) {
        requestedSpaceId = spaceId
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = MySpaceUiState.Loading
            _state.value = try {
                val spaces = getSpacesUseCase()
                val found = requestedSpaceId?.let { id -> spaces.firstOrNull { it.space.id == id } } ?: spaces.firstOrNull { it.isOwner }
                found?.let { MySpaceUiState.Loaded(it.space, it.plan, it.ageGroupsThisSeason, it.ageGroups.size, it.isOwner) } ?: MySpaceUiState.Failed
            } catch (e: Exception) {
                MySpaceUiState.Failed
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
        if (!dialog.canDelete || !loaded.isOwner) return
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

    fun onLeaveSpaceOpened() {
        _leaveDialog.value = LeaveSpaceDialog()
    }

    fun onLeaveSpaceDismissed() {
        if (_leaveDialog.value?.isLeaving != true) _leaveDialog.value = null
    }

    fun onLeavePasswordChanged(password: String) = _leaveDialog.update { it?.copy(password = password, error = null) }

    /** Leaves the space after the staff member's password; on success the dialog closes and [spaceLeft] fires. */
    fun onLeaveSpaceConfirmed() {
        val loaded = _state.value as? MySpaceUiState.Loaded ?: return
        val dialog = _leaveDialog.value ?: return
        if (!dialog.canLeave || loaded.isOwner) return
        viewModelScope.launch {
            _leaveDialog.update { it?.copy(isLeaving = true, error = null) }
            try {
                leaveSpaceUseCase(loaded.space.id, dialog.password)
                _leaveDialog.value = null
                _spaceLeft.tryEmit(Unit)
            } catch (e: Exception) {
                val error = if (e.isWrongCurrentPassword() || e.isSignedOutForWrongPasswords()) LeaveSpaceError.WRONG_PASSWORD else LeaveSpaceError.FAILED
                _leaveDialog.update { it?.copy(isLeaving = false, error = error) }
            }
        }
    }
}
