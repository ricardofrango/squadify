package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.exception.ForbiddenException
import com.ricardofrango.footballmanager.exception.ValidationException
import com.ricardofrango.footballmanager.model.*
import kotlin.uuid.Uuid

class AddStaffToTeamUseCase(
    private val teamRepository: TeamRepository,
    private val userRepository: UserRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase
) {
    suspend operator fun invoke(userId: String, teamId: String, request: AddStaffToTeamRequest): Boolean {
        // 1. Verify team exists first to get context
        val team = teamRepository.getTeamById(teamId)
            ?: throw ValidationException("Team not found")

        // 2. Check permissions (Admin/Coordinator required at club level)
        checkUserPermissionUseCase(
            userId = userId,
            clubId = team.clubId,
            minClubRole = ClubRole.COORDINATOR,
            errorMessage = "Only admins or coordinators can add staff to teams"
        )

        // 3. Verify user exists AND is already a member of this club
        userRepository.getUserRoleInClub(request.userId, team.clubId)
            ?: throw ForbiddenException("User must be a member of the club before being added to a team")

        val teamStaff = TeamStaff(
            id = Uuid.random().toString(),
            teamId = teamId,
            userId = request.userId,
            role = request.role
        )

        return teamRepository.addStaffToTeam(teamStaff)
    }
}
