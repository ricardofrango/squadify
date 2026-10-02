package com.ricardofrango.squadify.presentation.ui.editclub

import com.ricardofrango.squadify.model.ClubMembersOverview
import com.ricardofrango.squadify.model.ClubPermissionSetting
import com.ricardofrango.squadify.model.ConfigurablePermission
import com.ricardofrango.squadify.model.TeamRole
import com.ricardofrango.squadify.model.UpdateClubRequest

// Club settings tabs (design 34): Details · Members · Permissions. Always opens on Details.
const val EDIT_CLUB_TAB_DETAILS = 0
const val EDIT_CLUB_TAB_MEMBERS = 1
const val EDIT_CLUB_TAB_PERMISSIONS = 2

/** The Members tab's role filter (34a). */
enum class MembersFilter { ALL, ADMINS, COORDINATORS, MEMBERS, PENDING }

/** The Members tab loads on its own, so an error stays inside the tab (Details and Permissions still work). */
sealed class MembersUiState {
    data object Loading : MembersUiState()
    data object Error : MembersUiState()
    data class Loaded(val overview: ClubMembersOverview) : MembersUiState()
}

/** Everything `UpdateClubRequest` can carry, held as plain editable fields -- same "blank optional field
 * means null" trick as `ProfileFormFields`, so the request only ever overwrites a field with something
 * the user actually typed. */
data class ClubFormFields(
    val name: String,
    val shortName: String,
    val email: String,
    val phone: String,
    val address: String,
    val stadiumLocation: String
) {
    fun trimmed(): ClubFormFields = ClubFormFields(
        name = name.trim(),
        shortName = shortName.trim(),
        email = email.trim(),
        phone = phone.trim(),
        address = address.trim(),
        stadiumLocation = stadiumLocation.trim()
    )

    fun toRequest(): UpdateClubRequest = trimmed().let {
        UpdateClubRequest(
            name = it.name,
            shortName = it.shortName.takeIf(String::isNotBlank),
            email = it.email.takeIf(String::isNotBlank),
            phone = it.phone.takeIf(String::isNotBlank),
            address = it.address.takeIf(String::isNotBlank),
            stadiumLocation = it.stadiumLocation.takeIf(String::isNotBlank)
        )
    }
}

sealed class EditClubUiState {
    data object Loading : EditClubUiState()
    data class Error(val message: String) : EditClubUiState()

    data class Idle(
        val selectedTab: Int = EDIT_CLUB_TAB_DETAILS,
        val original: ClubFormFields,
        val form: ClubFormFields,
        val isSubmitting: Boolean = false,
        val submitError: String? = null,
        // Permissions tab -- loaded alongside the club itself (see EditClubViewModel.load), with its
        // own independent dirty/submit/error state, same "two unrelated save actions on one screen"
        // shape as AccountUiState.Idle's profile vs. password split.
        val originalPermissions: List<ClubPermissionSetting>,
        val permissions: List<ClubPermissionSetting>,
        val isSubmittingPermissions: Boolean = false,
        val permissionsSubmitError: String? = null
    ) : EditClubUiState() {
        val isDirty: Boolean get() = form != original
        val isValid: Boolean get() = form.name.isNotBlank()
        val canSave: Boolean get() = isDirty && isValid && !isSubmitting

        // The short name ("CDR") already reads as a logo placeholder as-is; only derive initials from
        // the full name when there's no short name to fall back on.
        val initials: String get() = form.shortName.trim().takeIf { it.isNotBlank() }
            ?: form.name.trim().split(" ")
                .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                .take(2)
                .joinToString("")

        val permissionsDirty: Boolean get() = permissions != originalPermissions
        val canSavePermissions: Boolean get() = permissionsDirty && !isSubmittingPermissions

        fun minTeamRoleFor(action: ConfigurablePermission): TeamRole? =
            permissions.first { it.action == action }.minTeamRole
    }
}
