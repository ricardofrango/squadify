package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.domain.service.TransactionManager
import com.ricardofrango.footballmanager.exception.ForbiddenException
import com.ricardofrango.footballmanager.model.ClubRole
import com.ricardofrango.footballmanager.model.TeamRole
import com.ricardofrango.footballmanager.model.UserStatus

class CheckUserPermissionUseCase(
    private val userRepository: UserRepository,
    private val teamRepository: TeamRepository,
    private val playerRepository: PlayerRepository,
    private val transactionManager: TransactionManager
) {
    /**
     * Checks if a user has permission at the club or team level.
     * Platform Admins always have permission.
     * 
     * @param userId The ID of the user performing the action.
     * @param clubId The ID of the club context.
     * @param minClubRole The minimum ClubRole required (e.g., COORDINATOR).
     * @param minTeamRole The minimum TeamRole required if teamId is provided.
     * @param teamId Optional team ID for team-specific operations.
     * @param allowPlayerOrParentAccess When true, also grants access if the caller is linked (via
     *   `UserPlayers`) to a player in this club (and, when `teamId` is given, specifically on that
     *   team via `TeamPlayers`). Defaults to false and must be explicitly opted into by READ use cases
     *   only -- see the DESIGN INVARIANT below.
     * @param errorMessage Optional custom error message.
     * @throws ForbiddenException if permission is denied.
     *
     * DESIGN INVARIANT (see "issues to fix.md" and PERMISSION_TEST_MATRIX.md): the player/parent path
     * below is opt-in via `allowPlayerOrParentAccess`, which defaults to false. No write/mutation use
     * case may ever pass `allowPlayerOrParentAccess = true` -- players and family members must never
     * gain write access. This is enforced by discipline + tests, not the type system: search for
     * `allowPlayerOrParentAccess = true` before adding it to any new call site and confirm the use case
     * is read-only.
     */
    suspend operator fun invoke(
        userId: String,
        clubId: String,
        minClubRole: ClubRole? = null,
        minTeamRole: TeamRole? = null,
        teamId: String? = null,
        allowPlayerOrParentAccess: Boolean = false,
        errorMessage: String = "Insufficient permissions to perform this action"
    ) = transactionManager.runInTransaction {
        val user = userRepository.findById(userId) ?: throw ForbiddenException("User not found")

        // 0. A non-active account (suspended/inactive) is denied outright, even a platform admin's
        if (user.status != UserStatus.ACTIVE) throw ForbiddenException("User account is not active")

        // 1. Platform Admins have "God Mode"
        if (user.isPlatformAdmin) return@runInTransaction

        // 2. Check Club Level Role
        val userClubRole = userRepository.getUserRoleInClub(userId, clubId)
        
        // If they are Club Admin or Coordinator, they have global access in the club
        if (userClubRole != null) {
            if (userClubRole == ClubRole.CLUB_ADMIN || userClubRole == ClubRole.COORDINATOR) {
                // If we specifically required a CLUB_ADMIN and they are only COORDINATOR, we might still fail
                // but usually COORDINATOR is enough for most things except sensitive club settings.
                if (minClubRole == null || userClubRole.level >= minClubRole.level) return@runInTransaction
            }
            
            // If we only needed a club membership (MEMBER level 1)
            if (minClubRole != null && minClubRole == ClubRole.MEMBER && userClubRole.level >= ClubRole.MEMBER.level) {
                // For team-specific operations, we still need to check team role if not coordinator+
                if (teamId == null) return@runInTransaction
            }
        }

        // 3. Check Team Level Role if teamId is provided
        if (teamId != null && minTeamRole != null) {
            // isUserStaffOfTeam should be updated to return the TeamRole? Or we check specific level.
            // For now, let's assume if they are staff, they have permission if minTeamRole is reached.
            // We'll need to update TeamRepository to get the role.
            val userTeamRole = teamRepository.getUserRoleInTeam(userId, teamId)
            if (userTeamRole != null && userTeamRole.level >= minTeamRole.level) {
                return@runInTransaction
            }
        }

        // 4. Check Player/Parent link via TeamPlayers/UserPlayers -- READ-ONLY, opt-in only
        if (allowPlayerOrParentAccess) {
            val linkedPlayerIds = playerRepository.getPlayersByUser(userId)
                .filter { it.clubId == clubId }
                .map { it.id }
                .toSet()
            if (linkedPlayerIds.isNotEmpty()) {
                if (teamId == null) return@runInTransaction
                val isOnRequestedTeam = playerRepository.getTeamPlayersByClub(clubId)
                    .any { it.playerId in linkedPlayerIds && it.teamId == teamId }
                if (isOnRequestedTeam) return@runInTransaction
            }
        }

        throw ForbiddenException(errorMessage)
    }
}
