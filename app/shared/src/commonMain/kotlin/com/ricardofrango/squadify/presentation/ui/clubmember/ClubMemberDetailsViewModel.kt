package com.ricardofrango.squadify.presentation.ui.clubmember

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.domain.usecase.CancelClubInviteUseCase
import com.ricardofrango.squadify.domain.usecase.GetClubMembersOverviewUseCase
import com.ricardofrango.squadify.domain.usecase.RemoveClubMemberUseCase
import com.ricardofrango.squadify.domain.usecase.ResendClubInviteUseCase
import com.ricardofrango.squadify.domain.usecase.UpdateClubMemberRoleUseCase
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.presentation.ui.editclub.labelRes
import com.ricardofrango.squadify.presentation.errorMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.member_now_role
import squadify.app.shared.generated.resources.member_remove_failed
import squadify.app.shared.generated.resources.member_removed
import squadify.app.shared.generated.resources.member_role_failed
import squadify.app.shared.generated.resources.member_you_now_role
import squadify.app.shared.generated.resources.members_action_failed
import squadify.app.shared.generated.resources.members_invite_cancelled
import squadify.app.shared.generated.resources.members_invite_resent

/**
 * Club member details (design 35): one member or one pending invite, picked out of the club's members
 * overview -- the same payload as the Members tab, so the two can't disagree.
 */
class ClubMemberDetailsViewModel(
    private val getClubMembersOverviewUseCase: GetClubMembersOverviewUseCase,
    private val updateClubMemberRoleUseCase: UpdateClubMemberRoleUseCase,
    private val resendClubInviteUseCase: ResendClubInviteUseCase,
    private val cancelClubInviteUseCase: CancelClubInviteUseCase,
    private val removeClubMemberUseCase: RemoveClubMemberUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<ClubMemberDetailsUiState>(ClubMemberDetailsUiState.Loading)
    val state: StateFlow<ClubMemberDetailsUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ClubMemberDetailsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<ClubMemberDetailsEvent> = _events.asSharedFlow()

    private var loadedKey: String? = null

    fun load(clubId: String, userId: String?, inviteId: String?, force: Boolean = false) {
        val key = "$clubId/$userId/$inviteId"
        if (!force && loadedKey == key) return
        loadedKey = key
        viewModelScope.launch {
            _state.value = ClubMemberDetailsUiState.Loading
            _state.value = try {
                val overview = getClubMembersOverviewUseCase(clubId)
                val member = userId?.let { id -> overview.members.firstOrNull { it.userId == id } }
                val invite = inviteId?.let { id -> overview.pendingInvites.firstOrNull { it.inviteId == id } }
                when {
                    member != null -> ClubMemberDetailsUiState.Member(overview, member)
                    invite != null -> ClubMemberDetailsUiState.Invite(overview, invite)
                    else -> ClubMemberDetailsUiState.Error
                }
            } catch (e: Exception) {
                ClubMemberDetailsUiState.Error
            }
        }
    }

    fun selectRole(role: ClubRole) {
        _state.update { (it as? ClubMemberDetailsUiState.Member)?.takeIf { m -> !m.saving }?.copy(selectedRole = role) ?: it }
    }

    fun saveRole(clubId: String) {
        val current = _state.value as? ClubMemberDetailsUiState.Member ?: return
        if (!current.canSave) return
        val role = current.selectedRole
        _state.value = current.copy(saving = true)
        viewModelScope.launch {
            try {
                updateClubMemberRoleUseCase(clubId, current.member.userId, role)
                val roleName = getString(role.labelRes())
                if (current.isYou) {
                    // No reload: as a Member you can no longer read this list. Show the new role and leave.
                    _state.value = ClubMemberDetailsUiState.Member(current.overview, current.member.copy(role = role))
                    _events.emit(ClubMemberDetailsEvent.LeftSettings(getString(Res.string.member_you_now_role, roleName)))
                    return@launch
                }
                // Refresh from the server: admin counts and the viewer's own role may have changed.
                val overview = getClubMembersOverviewUseCase(clubId)
                val member = overview.members.firstOrNull { it.userId == current.member.userId } ?: current.member.copy(role = role)
                _state.value = ClubMemberDetailsUiState.Member(overview, member)
                _events.emit(ClubMemberDetailsEvent.Message(getString(Res.string.member_now_role, "${member.firstName} ${member.lastName}", roleName)))
            } catch (e: Exception) {
                _state.value = current.copy(saving = false)
                _events.emit(ClubMemberDetailsEvent.Message(errorMessage(e, Res.string.member_role_failed)))
            }
        }
    }

    /** Remove from club (design 35): back to the list, which says so. */
    fun removeMember(clubId: String) {
        val current = _state.value as? ClubMemberDetailsUiState.Member ?: return
        if (!current.canRemove) return
        _state.value = current.copy(saving = true)
        viewModelScope.launch {
            try {
                removeClubMemberUseCase(clubId, current.member.userId)
                _events.emit(ClubMemberDetailsEvent.Closed(getString(Res.string.member_removed, "${current.member.firstName} ${current.member.lastName}")))
            } catch (e: Exception) {
                _state.value = current.copy(saving = false)
                _events.emit(ClubMemberDetailsEvent.Message(errorMessage(e, Res.string.member_remove_failed)))
            }
        }
    }

    fun resendInvite(clubId: String) {
        val current = _state.value as? ClubMemberDetailsUiState.Invite ?: return
        _state.value = current.copy(busy = true)
        viewModelScope.launch {
            try {
                resendClubInviteUseCase(clubId, current.invite.inviteId)
                _events.emit(ClubMemberDetailsEvent.Message(getString(Res.string.members_invite_resent, current.invite.email)))
            } catch (e: Exception) {
                _events.emit(ClubMemberDetailsEvent.Message(errorMessage(e, Res.string.members_action_failed)))
            } finally {
                _state.value = current.copy(busy = false)
            }
        }
    }

    fun cancelInvite(clubId: String) {
        val current = _state.value as? ClubMemberDetailsUiState.Invite ?: return
        _state.value = current.copy(busy = true)
        viewModelScope.launch {
            try {
                cancelClubInviteUseCase(clubId, current.invite.inviteId)
                _events.emit(ClubMemberDetailsEvent.Closed(getString(Res.string.members_invite_cancelled, current.invite.email)))
            } catch (e: Exception) {
                _state.value = current.copy(busy = false)
                _events.emit(ClubMemberDetailsEvent.Message(errorMessage(e, Res.string.members_action_failed)))
            }
        }
    }
}
