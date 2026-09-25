package com.ricardofrango.squadify.presentation.ui.editclub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.domain.usecase.GetClubPermissionsUseCase
import com.ricardofrango.squadify.domain.usecase.GetClubUseCase
import com.ricardofrango.squadify.domain.usecase.UpdateClubPermissionsUseCase
import com.ricardofrango.squadify.domain.usecase.UpdateClubUseCase
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.model.ClubPermissionSetting
import com.ricardofrango.squadify.model.ConfigurablePermission
import com.ricardofrango.squadify.model.TeamRole
import com.ricardofrango.squadify.model.UpdateClubPermissionsRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EditClubViewModel(
    private val getClubUseCase: GetClubUseCase,
    private val updateClubUseCase: UpdateClubUseCase,
    private val getClubPermissionsUseCase: GetClubPermissionsUseCase,
    private val updateClubPermissionsUseCase: UpdateClubPermissionsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<EditClubUiState>(EditClubUiState.Loading)
    val state: StateFlow<EditClubUiState> = _state.asStateFlow()

    fun load(clubId: String) {
        viewModelScope.launch {
            _state.update { EditClubUiState.Loading }
            _state.update {
                try {
                    val club = getClubUseCase(clubId)
                    val permissions = getClubPermissionsUseCase(clubId).settings
                    idleFor(club, permissions)
                } catch (e: Exception) {
                    EditClubUiState.Error(e.message ?: "Couldn't load this club. Please try again.")
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
                _state.update {
                    (it as EditClubUiState.Idle).copy(
                        isSubmitting = false,
                        submitError = e.message ?: "Couldn't save your changes. Please try again."
                    )
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
                _state.update {
                    (it as EditClubUiState.Idle).copy(
                        isSubmittingPermissions = false,
                        permissionsSubmitError = e.message ?: "Couldn't save these permissions. Please try again."
                    )
                }
            }
        }
    }
}
