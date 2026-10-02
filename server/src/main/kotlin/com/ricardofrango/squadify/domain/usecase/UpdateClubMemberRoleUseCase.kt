package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.exception.ConflictException
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.UpdateClubMemberRoleRequest

class UpdateClubMemberRoleUseCase(
    private val clubRepository: ClubRepository,
    private val userRepository: UserRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(
        userId: String,
        clubId: String,
        targetUserId: String,
        request: UpdateClubMemberRoleRequest
    ): Boolean = transactionManager.runInTransaction {
        // 1. Check permissions (Only CLUB_ADMIN can manage user roles at the club level)
        checkUserPermissionUseCase(
            userId = userId,
            clubId = clubId,
            minClubRole = ClubRole.COORDINATOR,
            errorMessage = "Only club admins or coordinators can manage user roles at the club level"
        )

        // 2. Admin is guarded (design 34): only admins grant or remove it, and the club always keeps at
        // least one admin (so the last admin can't be demoted, by anyone, including themselves).
        val members = clubRepository.getClubMembers(clubId)
        val target = members.firstOrNull { it.userId == targetUserId }
            ?: throw NotFoundException("User is not a member of this club")
        val touchesAdmin = request.role == ClubRole.CLUB_ADMIN || target.role == ClubRole.CLUB_ADMIN
        if (touchesAdmin && target.role != request.role) {
            val callerIsAdmin = userRepository.getUserRoleInClub(userId, clubId) == ClubRole.CLUB_ADMIN ||
                userRepository.findById(userId)?.isPlatformAdmin == true
            if (!callerIsAdmin) throw ForbiddenException("Only admins can grant or remove Admin")
            if (target.role == ClubRole.CLUB_ADMIN && members.count { it.role == ClubRole.CLUB_ADMIN } <= 1) {
                throw ConflictException("The club needs at least one admin")
            }
        }

        // 3. Update role in the club_members table -- updateMemberRole reports whether it actually
        // matched a row (targetUserId must already be a member of clubId), so a caller targeting a
        // non-member (stale UI, typo'd id, someone just removed) gets a clear 404 instead of a silent
        // no-op success.
        val updated = clubRepository.updateMemberRole(clubId, targetUserId, request.role)
        if (!updated) {
            throw NotFoundException("User is not a member of this club")
        }
        true
    }
}
