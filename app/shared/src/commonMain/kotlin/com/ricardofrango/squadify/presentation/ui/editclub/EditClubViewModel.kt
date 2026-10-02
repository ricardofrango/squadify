package com.ricardofrango.squadify.presentation.ui.editclub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.domain.usecase.AddClubMembersUseCase
import com.ricardofrango.squadify.domain.usecase.CancelClubInviteUseCase
import com.ricardofrango.squadify.domain.usecase.GetClubMembersOverviewUseCase
import com.ricardofrango.squadify.domain.usecase.GetClubPermissionsUseCase
import com.ricardofrango.squadify.domain.usecase.ResendClubInviteUseCase
import com.ricardofrango.squadify.domain.usecase.GetClubUseCase
import com.ricardofrango.squadify.domain.usecase.UpdateClubPermissionsUseCase
import com.ricardofrango.squadify.domain.usecase.UpdateClubUseCase
import com.ricardofrango.squadify.model.AddClubMembersRequest
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ClubPermissionSetting
import com.ricardofrango.squadify.model.ConfigurablePermission
import com.ricardofrango.squadify.model.TeamRole
import com.ricardofrango.squadify.model.UpdateClubPermissionsRequest
import com.ricardofrango.squadify.presentation.errorMessage
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.members_action_failed
import squadify.app.shared.generated.resources.members_invite_cancelled
import squadify.app.shared.generated.resources.members_invite_failed
import squadify.app.shared.generated.resources.members_invite_resent
import squadify.app.shared.generated.resources.members_invite_sent
import squadify.app.shared.generated.resources.msg_club_change_failed
import squadify.app.shared.generated.resources.msg_failed_to_load_club
import squadify.app.shared.generated.resources.msg_permission_denied
import squadify.app.shared.generated.resources.msg_permissions_save_failed
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

class EditClubViewModel(
    private val getClubUseCase: GetClubUseCase,
    private val updateClubUseCase: UpdateClubUseCase,
    private val getClubPermissionsUseCase: GetClubPermissionsUseCase,
    private val updateClubPermissionsUseCase: UpdateClubPermissionsUseCase,
    private val getClubMembersOverviewUseCase: GetClubMembersOverviewUseCase,
    private val addClubMembersUseCase: AddClubMembersUseCase,
    private val resendClubInviteUseCase: ResendClubInviteUseCase,
    private val cancelClubInviteUseCase: CancelClubInviteUseCase
) : ViewModel() {

    private val _members = MutableStateFlow<MembersUiState>(MembersUiState.Loading)
    val members: StateFlow<MembersUiState> = _members.asStateFlow()

    // One-off confirmations ("Invite sent to …") shown as a snackbar.
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _inviting = MutableStateFlow(false)
    val inviting: StateFlow<Boolean> = _inviting.asStateFlow()

    fun loadMembers(clubId: String) {
        viewModelScope.launch {
            _members.update { MembersUiState.Loading }
            _members.update {
                try {
                    MembersUiState.Loaded(getClubMembersOverviewUseCase(clubId))
                } catch (e: Exception) {
                    MembersUiState.Error
                }
            }
        }
    }

    /** Invite by email as Member or Coordinator (34). [onDone] closes the sheet on success. */
    fun invite(clubId: String, email: String, role: ClubRole, onDone: () -> Unit) {
        viewModelScope.launch {
            _inviting.value = true
            try {
                addClubMembersUseCase(clubId, AddClubMembersRequest(listOf(email.trim()), role))
                _messages.emit(getString(Res.string.members_invite_sent, email.trim()))
                onDone()
                loadMembers(clubId)
            } catch (e: Exception) {
                _messages.emit(errorMessage(e, Res.string.members_invite_failed))
            } finally {
                _inviting.value = false
            }
        }
    }

    fun resendInvite(clubId: String, inviteId: String, email: String) {
        viewModelScope.launch {
            try {
                resendClubInviteUseCase(clubId, inviteId)
                _messages.emit(getString(Res.string.members_invite_resent, email))
            } catch (e: Exception) {
                _messages.emit(errorMessage(e, Res.string.members_action_failed))
            }
        }
    }

    fun cancelInvite(clubId: String, inviteId: String, email: String) {
        viewModelScope.launch {
            try {
                cancelClubInviteUseCase(clubId, inviteId)
                _messages.emit(getString(Res.string.members_invite_cancelled, email))
                loadMembers(clubId)
            } catch (e: Exception) {
                _messages.emit(errorMessage(e, Res.string.members_action_failed))
            }
        }
    }

    private val _state = MutableStateFlow<EditClubUiState>(EditClubUiState.Loading)
    val state: StateFlow<EditClubUiState> = _state.asStateFlow()

    private var loadedClubId: String? = null

    fun load(clubId: String, force: Boolean = false) {
        if (!force && loadedClubId == clubId) return
        loadedClubId = clubId
        viewModelScope.launch {
            _state.update { EditClubUiState.Loading }
            _state.update {
                try {
                    val details = getClubUseCase(clubId)
                    if (!details.canEditClub) {
                        // Defense in depth: the "Edit club details" menu item is already hidden from a
                        // non-editor (ClubDetailsScreen's "More" menu), but this screen must refuse to
                        // show editable content (including the Permissions tab) to anyone who reaches it
                        // some other way -- e.g. a direct web deep link -- not just rely on the save
                        // actions' own server-side 403.
                        EditClubUiState.Error(getString(Res.string.msg_permission_denied))
                    } else {
                        val permissions = getClubPermissionsUseCase(clubId).settings
                        loadMembers(clubId)
                        idleFor(details.club, permissions)
                    }
                } catch (e: Exception) {
                    EditClubUiState.Error(errorMessage(e, Res.string.msg_failed_to_load_club))
                }
            }
        }
    }

    private fun idleFor(club: Club, permissions: List<ClubPermissionSetting>): EditClubUiState.Idle {
        val fields = ClubFormFields(
            name = club.name,
            shortName = club.shortName.orEmpty(),
            email = club.email.orEmpty(),
            phone = club.phone.orEmpty(),
            address = club.address.orEmpty(),
            stadiumLocation = club.stadiumLocation.orEmpty()
        )
        return EditClubUiState.Idle(
            original = fields,
            form = fields,
            originalPermissions = permissions,
            permissions = permissions
        )
    }

    fun onTabSelected(tab: Int) {
        _state.update { current -> (current as? EditClubUiState.Idle)?.copy(selectedTab = tab) ?: current }
    }

    fun updateForm(form: ClubFormFields) {
        _state.update { current ->
            (current as? EditClubUiState.Idle)?.copy(form = form, submitError = null) ?: current
        }
    }

    fun onSaveClicked(clubId: String, onSaved: () -> Unit) {
        val idle = _state.value as? EditClubUiState.Idle ?: return
        if (!idle.canSave) return

        viewModelScope.launch {
            _state.update { (it as EditClubUiState.Idle).copy(isSubmitting = true, submitError = null) }
            try {
                updateClubUseCase(clubId, idle.form.toRequest())
                val saved = idle.form.trimmed()
                _state.update {
                    (it as EditClubUiState.Idle).copy(
                        original = saved,
                        form = saved,
                        isSubmitting = false
                    )
                }
                onSaved()
            } catch (e: Exception) {
                val message = errorMessage(e, Res.string.msg_club_change_failed)
                _state.update {
                    (it as EditClubUiState.Idle).copy(isSubmitting = false, submitError = message)
                }
            }
        }
    }

    fun onPermissionChanged(action: ConfigurablePermission, minTeamRole: TeamRole?) {
        _state.update { current ->
            val idle = current as? EditClubUiState.Idle ?: return@update current
            val updated = idle.permissions.map {
                if (it.action == action) ClubPermissionSetting(action, minTeamRole) else it
            }
            idle.copy(permissions = updated, permissionsSubmitError = null)
        }
    }

    fun onSavePermissionsClicked(clubId: String) {
        val idle = _state.value as? EditClubUiState.Idle ?: return
        if (!idle.canSavePermissions) return

        viewModelScope.launch {
            _state.update { (it as EditClubUiState.Idle).copy(isSubmittingPermissions = true, permissionsSubmitError = null) }
            try {
                updateClubPermissionsUseCase(clubId, UpdateClubPermissionsRequest(idle.permissions))
                _state.update {
                    (it as EditClubUiState.Idle).copy(
                        originalPermissions = idle.permissions,
                        isSubmittingPermissions = false
                    )
                }
            } catch (e: Exception) {
                val message = errorMessage(e, Res.string.msg_permissions_save_failed)
                _state.update {
                    (it as EditClubUiState.Idle).copy(isSubmittingPermissions = false, permissionsSubmitError = message)
                }
            }
        }
    }
}
