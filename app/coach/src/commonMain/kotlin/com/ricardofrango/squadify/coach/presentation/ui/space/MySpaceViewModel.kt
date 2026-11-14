package com.ricardofrango.squadify.coach.presentation.ui.space

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.coach.domain.DeleteSpaceUseCase
import com.ricardofrango.squadify.coach.domain.GetSpacesUseCase
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

sealed class MySpaceUiState {
    data object Loading : MySpaceUiState()
    data object Failed : MySpaceUiState()

    /**
     * The owner's space, with its [plan] and [ageGroupsThisSeason] for the Plan card, and [ageGroupCount]: every
     * age group it has, what deleting it takes with it.
     */
    data class Loaded(
        val space: Club,
        val plan: SpacePlan,
        val ageGroupsThisSeason: Int = 0,
        val ageGroupCount: Int = 0
    ) : MySpaceUiState()
}

/**
 * The owner's own space (the ☰ menu's My club or team): its name, sport and plan, and deleting it. A space has
 * no logo (the coach app shows it with the Squadify mark), so there's nothing to change here but the plan's card and
 * the deletion.
 */
class MySpaceViewModel(
    private val getSpacesUseCase: GetSpacesUseCase,
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
}
