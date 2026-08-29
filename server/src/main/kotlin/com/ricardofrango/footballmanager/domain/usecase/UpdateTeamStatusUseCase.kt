package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.exception.ForbiddenException
import com.ricardofrango.footballmanager.exception.ValidationException
import com.ricardofrango.footballmanager.model.Role

class UpdateTeamStatusUseCase(
    private val teamRepository: TeamRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase
) {
    suspend operator fun invoke(
        userId: String,
        teamId: String,
        active: Boolean
    ): Boolean {
        // 1. Verify team exists first to get context
        val team = teamRepository.getTeamById(teamId)
            ?: throw ValidationException("Team not found")

        // 2. Check permissions using team's clubId
        checkUserPermissionUseCase(
            userId = userId,
            clubId = team.clubId,
            minRole = Role.COORDINATOR,
            teamId = teamId,
            errorMessage = "Only admins or coordinators can change team status"
        )

        return teamRepository.updateActiveStatus(teamId, active)
    }
}
