package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.exception.ForbiddenException
import com.ricardofrango.footballmanager.exception.ValidationException
import com.ricardofrango.footballmanager.model.Role
import com.ricardofrango.footballmanager.model.Team

class CreateTeamUseCase(
    private val teamRepository: TeamRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(userId: String, team: Team): Boolean {
        // Permission check: Only CLUB_ADMIN or COORDINATOR can create teams
        val userRole = userRepository.getUserRoleInClub(userId, team.clubId)
        if (userRole != Role.CLUB_ADMIN && userRole != Role.COORDINATOR) {
            throw ForbiddenException("Only admins or coordinators can create teams")
        }

        if (team.name.isBlank()) {
            throw ValidationException("Team name cannot be empty")
        }

        return teamRepository.createTeam(team)
    }
}
