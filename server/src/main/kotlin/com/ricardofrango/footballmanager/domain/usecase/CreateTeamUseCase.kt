package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.model.Role
import com.ricardofrango.footballmanager.model.Team

class CreateTeamUseCase(
    private val teamRepository: TeamRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(userId: String, team: Team): Result<Boolean> {
        // Permission check: Only CLUB_ADMIN or COORDINATOR can create teams
        val userRole = userRepository.getUserRoleInClub(userId, team.clubId)
        if (userRole != Role.CLUB_ADMIN && userRole != Role.COORDINATOR) {
            return Result.failure(IllegalAccessException("Only admins or coordinators can create teams"))
        }

        if (team.name.isBlank()) {
            return Result.failure(IllegalArgumentException("Team name cannot be empty"))
        }

        return try {
            val success = teamRepository.createTeam(team)
            Result.success(success)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
