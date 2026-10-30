package com.ricardofrango.squadify.coach.presentation.ui.invite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.coach.domain.CreateStaffInviteUseCase
import com.ricardofrango.squadify.coach.domain.GetStaffInvitesUseCase
import com.ricardofrango.squadify.coach.domain.RevokeStaffInviteUseCase
import com.ricardofrango.squadify.model.StaffInvite
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class InviteStaffUiState {
    data object Loading : InviteStaffUiState()
    data object Failed : InviteStaffUiState()

    /** The codes still waiting, the newest first. */
    data class Loaded(val invites: List<StaffInvite>, val isCreating: Boolean = false) : InviteStaffUiState()
}

/** One-off messages, for the snackbar. */
enum class InviteStaffMessage { CREATE_FAILED, WITHDRAWN, WITHDRAW_FAILED }

class InviteStaffViewModel(
    private val getStaffInvitesUseCase: GetStaffInvitesUseCase,
    private val createStaffInviteUseCase: CreateStaffInviteUseCase,
    private val revokeStaffInviteUseCase: RevokeStaffInviteUseCase
) : ViewModel() {
    private val _state = MutableStateFlow<InviteStaffUiState>(InviteStaffUiState.Loading)
    val state: StateFlow<InviteStaffUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<InviteStaffMessage>()
    val messages: SharedFlow<InviteStaffMessage> = _messages.asSharedFlow()

    fun load(ageGroupId: String) {
        viewModelScope.launch {
            _state.value = InviteStaffUiState.Loading
            _state.value = try {
                InviteStaffUiState.Loaded(getStaffInvitesUseCase(ageGroupId).sortedByDescending { it.createdAt })
            } catch (e: Exception) {
                InviteStaffUiState.Failed
            }
        }
    }

    fun onCreateClicked(ageGroupId: String) {
        val loaded = _state.value as? InviteStaffUiState.Loaded ?: return
        if (loaded.isCreating) return
        viewModelScope.launch {
            _state.value = loaded.copy(isCreating = true)
            try {
                val invite = createStaffInviteUseCase(ageGroupId)
                _state.update { s -> (s as? InviteStaffUiState.Loaded)?.let { InviteStaffUiState.Loaded(listOf(invite) + it.invites) } ?: s }
            } catch (e: Exception) {
                _state.update { s -> (s as? InviteStaffUiState.Loaded)?.copy(isCreating = false) ?: s }
                _messages.emit(InviteStaffMessage.CREATE_FAILED)
            }
        }
    }

    fun onWithdrawConfirmed(ageGroupId: String, code: String) {
        viewModelScope.launch {
            try {
                revokeStaffInviteUseCase(ageGroupId, code)
                _state.update { s -> (s as? InviteStaffUiState.Loaded)?.let { it.copy(invites = it.invites.filter { i -> i.code != code }) } ?: s }
                _messages.emit(InviteStaffMessage.WITHDRAWN)
            } catch (e: Exception) {
                _messages.emit(InviteStaffMessage.WITHDRAW_FAILED)
            }
        }
    }
}
