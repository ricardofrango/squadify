package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.model.Role

class ManageTeamUseCase(
    private val teamRepository: TeamRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(userId: String, clubId: String, teamId: String): Boolean {
        // Higher roles have access to all teams
        val userRole = userRepository.getUserRoleInClub(userId, clubId)
        if (userRole == Role.CLUB_ADMIN || userRole == Role.COORDINATOR) return true

        // Coaches only have access to their assigned teams
        if (userRole == Role.COACH) {
            return teamRepository.isUserStaffOfTeam(userId, teamId)
        }

        return false
    }
}
