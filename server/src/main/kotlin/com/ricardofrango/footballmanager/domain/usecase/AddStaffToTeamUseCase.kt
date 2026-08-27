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
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(userId: String, clubId: String, teamId: String, request: AddStaffToTeamRequest): Boolean {
        // 1. Check permissions (Only CLUB_ADMIN or COORDINATOR can add staff)
        val userRole = userRepository.getUserRoleInClub(userId, clubId)
        if (userRole != Role.CLUB_ADMIN && userRole != Role.COORDINATOR) {
            val user = userRepository.findById(userId)
            if (user?.isPlatformAdmin != true) {
                throw ForbiddenException("Only admins or coordinators can add staff to teams")
            }
        }

        // 2. Verify team exists and belongs to the club
        val team = teamRepository.getTeamById(teamId)
            ?: throw ValidationException("Team not found")
        
        if (team.clubId != clubId) {
            throw ForbiddenException("Team does not belong to this club")
        }

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
