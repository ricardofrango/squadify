package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.exception.ForbiddenException
import com.ricardofrango.footballmanager.exception.ValidationException
import com.ricardofrango.footballmanager.model.Role

class CheckUserPermissionUseCase(
    private val userRepository: UserRepository,
    private val teamRepository: TeamRepository
) {
    /**
     * Checks if a user has at least the minimum role required for a club or team operation.
     * Platform Admins always have permission.
     * 
     * @param userId The ID of the user performing the action.
     * @param clubId The ID of the club context.
     * @param minRole The minimum Role required.
     * @param teamId Optional team ID for team-specific operations.
     * @param errorMessage Optional custom error message if permission is denied.
     * @return The Role of the user in the club (or Role.CLUB_ADMIN if Platform Admin).
     * @throws ForbiddenException if user doesn't have required permission.
     * @throws ValidationException if teamId is missing for team-level operations.
     */
    suspend operator fun invoke(
        userId: String,
        clubId: String,
        minRole: Role,
        teamId: String? = null,
        errorMessage: String = "Insufficient permissions to perform this action"
    ): Role {
        val user = userRepository.findById(userId) ?: throw ForbiddenException("User not found")
        
        // 1. Platform Admins have "God Mode"
        if (user.isPlatformAdmin) return Role.CLUB_ADMIN

        // 2. Check Role level in the specific club
        val userRole = userRepository.getUserRoleInClub(userId, clubId)
        
        if (userRole == null || userRole.level < minRole.level) {
            throw ForbiddenException(errorMessage)
        }

        // 3. Coordinators and Admins have global club access
        if (userRole.level >= Role.COORDINATOR.level) {
            return userRole
        }

        // 4. Team-level permissions: If a teamId is provided, verify user is assigned to it
        if (teamId != null) {
            if (!teamRepository.isUserStaffOfTeam(userId, teamId)) {
                throw ForbiddenException("You do not have permission to manage this specific team")
            }
        }

        return userRole
    }
}
