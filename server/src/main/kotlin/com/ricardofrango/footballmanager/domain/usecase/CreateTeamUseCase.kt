package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.exception.ValidationException
import com.ricardofrango.footballmanager.model.ClubRole
import com.ricardofrango.footballmanager.model.CreateTeamRequest
import com.ricardofrango.footballmanager.model.Team
import kotlin.uuid.Uuid

class CreateTeamUseCase(
    private val teamRepository: TeamRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase
) {
    suspend operator fun invoke(userId: String, request: CreateTeamRequest): Team {
        // Permission check: Only CLUB_ADMIN or COORDINATOR can create teams
        checkUserPermissionUseCase(
            userId = userId,
            clubId = request.clubId,
            minClubRole = ClubRole.COORDINATOR,
            errorMessage = "Only admins or coordinators can create teams"
        )

        if (request.name.isBlank()) {
            throw ValidationException("Team name cannot be empty")
        }

        val team = Team(
            id = Uuid.random().toString(),
            clubId = request.clubId,
            ageGroupId = request.ageGroupId,
            seasonId = request.seasonId,
            name = request.name,
            code = request.code,
            active = true
        )

        return teamRepository.createTeam(team)
    }
}
