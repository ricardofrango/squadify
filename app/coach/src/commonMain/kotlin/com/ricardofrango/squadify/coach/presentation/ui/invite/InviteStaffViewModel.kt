package com.ricardofrango.squadify.coach.presentation.ui.invite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.coach.domain.CreateStaffInviteUseCase
import com.ricardofrango.squadify.coach.domain.GetStaffInvitesUseCase
import com.ricardofrango.squadify.coach.domain.InviteEmail
import com.ricardofrango.squadify.coach.domain.RevokeStaffInviteUseCase
import com.ricardofrango.squadify.data.remote.ApiException
import com.ricardofrango.squadify.data.remote.isNotInPlan
import com.ricardofrango.squadify.coach.domain.GetSpacesUseCase
import com.ricardofrango.squadify.domain.usecase.GetAgeGroupDetailsUseCase
import com.ricardofrango.squadify.model.SpacePlan
import com.ricardofrango.squadify.model.StaffInvite
import com.ricardofrango.squadify.model.hasRoomForStaff
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

    /**
     * The codes still waiting, the newest first, and the address typed to email one. [fullOnPlan]: the
     * space's plan when the age group has no room for more staff on it (no new code until it changes).
     */
    data class Loaded(
        val invites: List<StaffInvite>,
        val fullOnPlan: SpacePlan? = null,
        val isCreating: Boolean = false,
        val email: String = "",
        val isSending: Boolean = false,
        val isEmailInvalid: Boolean = false
    ) : InviteStaffUiState() {
        val canSend: Boolean get() = email.isNotBlank() && !isSending && !isCreating && fullOnPlan == null
    }
}

/** One-off messages, for the snackbar. */
enum class InviteStaffMessage { CREATE_FAILED, NO_ROOM_ON_PLAN, EMAIL_SENT, EMAIL_NOT_SENT, SEND_FAILED, WITHDRAWN, WITHDRAW_FAILED }

class InviteStaffViewModel(
    private val getStaffInvitesUseCase: GetStaffInvitesUseCase,
    private val getAgeGroupDetailsUseCase: GetAgeGroupDetailsUseCase,
    private val getSpacesUseCase: GetSpacesUseCase,
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
                coroutineScope {
                    // Whether the plan has room: the age group's staff against the owner's plan. If that
                    // can't be told, no box: creating a code still says so when there's no room.
                    val fullOnPlan = async {
                        runCatching {
                            val plan = getSpacesUseCase().firstOrNull { it.isOwner }?.plan
                            plan?.takeUnless { it.hasRoomForStaff(getAgeGroupDetailsUseCase(ageGroupId).staff.size) }
                        }.getOrNull()
                    }
                    InviteStaffUiState.Loaded(getStaffInvitesUseCase(ageGroupId).sortedByDescending { it.createdAt }, fullOnPlan.await())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                InviteStaffUiState.Failed
            }
        }
    }

    fun onCreateClicked(ageGroupId: String) {
        val loaded = _state.value as? InviteStaffUiState.Loaded ?: return
        if (loaded.isCreating || loaded.isSending || loaded.fullOnPlan != null) return
        viewModelScope.launch {
            _state.value = loaded.copy(isCreating = true)
            try {
                val invite = createStaffInviteUseCase(ageGroupId)
                _state.update { s -> (s as? InviteStaffUiState.Loaded)?.let { it.copy(invites = listOf(invite) + it.invites, isCreating = false) } ?: s }
            } catch (e: Exception) {
                _state.update { s -> (s as? InviteStaffUiState.Loaded)?.copy(isCreating = false) ?: s }
                _messages.emit(if (e.isNotInPlan) InviteStaffMessage.NO_ROOM_ON_PLAN else InviteStaffMessage.CREATE_FAILED)
            }
        }
    }

    fun onEmailChanged(email: String) {
        _state.update { s -> (s as? InviteStaffUiState.Loaded)?.copy(email = email, isEmailInvalid = false) ?: s }
    }

    /** Emails a new code to the typed address, in [language] (the app's). */
    fun onSendClicked(ageGroupId: String, language: String) {
        val loaded = _state.value as? InviteStaffUiState.Loaded ?: return
        if (!loaded.canSend) return
        if (!InviteEmail.isValid(loaded.email)) {
            _state.value = loaded.copy(isEmailInvalid = true)
            return
        }
        viewModelScope.launch {
            _state.value = loaded.copy(isSending = true)
            try {
                val invite = createStaffInviteUseCase(ageGroupId, loaded.email, language)
                _state.update { s -> (s as? InviteStaffUiState.Loaded)?.let { it.copy(invites = listOf(invite) + it.invites, email = "", isSending = false) } ?: s }
                _messages.emit(InviteStaffMessage.EMAIL_SENT)
            } catch (e: Exception) {
                _state.update { s -> (s as? InviteStaffUiState.Loaded)?.copy(isSending = false) ?: s }
                // A 400: the email didn't go (or today's limit is reached); no code was kept.
                _messages.emit(
                    when {
                        e.isNotInPlan -> InviteStaffMessage.NO_ROOM_ON_PLAN
                        (e as? ApiException)?.statusCode == 400 -> InviteStaffMessage.EMAIL_NOT_SENT
                        else -> InviteStaffMessage.SEND_FAILED
                    }
                )
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
