package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ConflictException
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.ClubRole

/**
 * Remove someone from a club (design 35): their membership and their staff roles in the club's age
 * groups go; their account and their parent/guardian links to players stay -- being a parent of a
 * player doesn't depend on a club role. They can be invited again later.
 */
class RemoveClubMemberUseCase(
    private val clubRepository: ClubRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val userRepository: UserRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, clubId: String, targetUserId: String) = transactionManager.runInTransaction {
        checkUserPermissionUseCase(
            userId = userId,
            clubId = clubId,
            minClubRole = ClubRole.COORDINATOR,
            errorMessage = "Only club admins or coordinators can remove members"
        )
        // Leaving a club is a different flow ("Leave club"), not this one.
        if (targetUserId == userId) throw ValidationException("You can't remove yourself from the club")

        val members = clubRepository.getClubMembers(clubId)
        val target = members.firstOrNull { it.userId == targetUserId }
            ?: throw NotFoundException("User is not a member of this club")
        if (target.role == ClubRole.CLUB_ADMIN) {
            val callerIsAdmin = userRepository.getUserRoleInClub(userId, clubId) == ClubRole.CLUB_ADMIN ||
                userRepository.findById(userId)?.isPlatformAdmin == true
            if (!callerIsAdmin) throw ForbiddenException("Only admins can remove an admin")
            // Only reachable by a platform admin: a club admin can't remove themselves.
            if (members.count { it.role == ClubRole.CLUB_ADMIN } <= 1) throw ConflictException("The club needs at least one admin")
        }

        ageGroupRepository.removeStaffFromClub(clubId, targetUserId)
        if (!clubRepository.removeMember(clubId, targetUserId)) throw NotFoundException("User is not a member of this club")
    }
}
