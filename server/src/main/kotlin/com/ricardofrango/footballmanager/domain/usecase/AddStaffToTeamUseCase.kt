package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.exception.ForbiddenException
import com.ricardofrango.footballmanager.exception.ValidationException
import com.ricardofrango.footballmanager.model.AddStaffToTeamRequest
import com.ricardofrango.footballmanager.model.Role
import com.ricardofrango.footballmanager.model.TeamStaff
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

        // 2. Check permissions using team's clubId
        checkUserPermissionUseCase(
            userId = userId,
            clubId = team.clubId,
            minRole = Role.COORDINATOR,
            teamId = teamId,
            errorMessage = "Only admins or coordinators can add staff to teams"
        )

        // 3. Verify staff user exists
        userRepository.findById(request.userId)
            ?: throw ValidationException("Staff user not found")

        val teamStaff = TeamStaff(
            id = Uuid.random().toString(),
            teamId = teamId,
            userId = request.userId,
            role = request.role
        )

        return teamRepository.addStaffToTeam(teamStaff)
    }
}
