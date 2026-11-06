package com.ricardofrango.squadify.presentation.ui.clubmember

import com.ricardofrango.squadify.model.ClubMemberEntry
import com.ricardofrango.squadify.model.ClubMembersOverview
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.PendingClubInvite

sealed class ClubMemberDetailsUiState {
    data object Loading : ClubMemberDetailsUiState()
    data object Error : ClubMemberDetailsUiState()

    data class Member(
        val overview: ClubMembersOverview,
        val member: ClubMemberEntry,
        // The option picked in the Club role card; "Make {role}" is enabled once it differs from the current role.
        val selectedRole: ClubRole = member.role,
        val saving: Boolean = false
    ) : ClubMemberDetailsUiState() {
        val isYou: Boolean get() = member.userId == overview.viewerUserId
        val canSave: Boolean get() = selectedRole != member.role && !saving && roleLock(overview, member, selectedRole) == null
        val canRemove: Boolean get() = canRemove(overview, member) && !saving
        // Take them off one of their age groups: admins and coordinators, as adding staff (the server's rule).
        val canRemoveFromAgeGroups: Boolean get() = overview.viewerRole == ClubRole.CLUB_ADMIN || overview.viewerRole == ClubRole.COORDINATOR
    }

    data class Invite(val overview: ClubMembersOverview, val invite: PendingClubInvite, val busy: Boolean = false) : ClubMemberDetailsUiState()
}

/** Why a club-role option can't be picked for [member] (design 34/35); the server enforces the same rules. */
enum class RoleLock { LAST_ADMIN, ADMIN_ONLY }

fun roleLock(overview: ClubMembersOverview, member: ClubMemberEntry, option: ClubRole): RoleLock? {
    if (option == member.role) return null
    val touchesAdmin = option == ClubRole.CLUB_ADMIN || member.role == ClubRole.CLUB_ADMIN
    if (touchesAdmin && overview.viewerRole != ClubRole.CLUB_ADMIN) return RoleLock.ADMIN_ONLY
    if (member.role == ClubRole.CLUB_ADMIN && overview.adminCount <= 1) return RoleLock.LAST_ADMIN
    return null
}

/**
 * Whether "Remove from club" shows (design 35), mirroring `RemoveClubMemberUseCase`: never on yourself
 * (that would be "Leave club"), an admin only by an admin, and never the club's last admin.
 */
fun canRemove(overview: ClubMembersOverview, member: ClubMemberEntry): Boolean {
    if (member.userId == overview.viewerUserId) return false
    if (member.role != ClubRole.CLUB_ADMIN) return true
    return overview.viewerRole == ClubRole.CLUB_ADMIN && overview.adminCount > 1
}

/** One-off effects for the screen to act on. */
sealed class ClubMemberDetailsEvent {
    data class Message(val text: String) : ClubMemberDetailsEvent()
    /** The invite was cancelled: nothing left to show, so go back to the list, which shows [text]. */
    data class Closed(val text: String) : ClubMemberDetailsEvent()
    /** You demoted yourself, and may have lost access to Club settings: show [text] and go back to Club details. */
    data class LeftSettings(val text: String) : ClubMemberDetailsEvent()
}
