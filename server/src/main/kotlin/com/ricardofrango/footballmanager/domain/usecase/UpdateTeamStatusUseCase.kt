package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.exception.ForbiddenException
import com.ricardofrango.footballmanager.exception.ValidationException
import com.ricardofrango.footballmanager.model.Role

class UpdateTeamStatusUseCase(
    private val teamRepository: TeamRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(userId: String, clubId: String, teamId: String, active: Boolean): Boolean {
        // 1. Check permissions
        val userRole = userRepository.getUserRoleInClub(userId, clubId)
        if (userRole != Role.CLUB_ADMIN && userRole != Role.COORDINATOR) {
            val user = userRepository.findById(userId)
            if (user?.isPlatformAdmin != true) {
                throw ForbiddenException("Only admins or coordinators can change team status")
            }
        }

        // 2. Verify team exists and belongs to the club
        val team = teamRepository.getTeamById(teamId)
            ?: throw ValidationException("Team not found")
        
        if (team.clubId != clubId) {
            throw ForbiddenException("Team does not belong to this club")
        }

        return teamRepository.updateActiveStatus(teamId, active)
    }
}
