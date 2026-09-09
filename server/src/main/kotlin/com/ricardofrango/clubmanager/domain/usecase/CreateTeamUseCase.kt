package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.AgeGroupRepository
import com.ricardofrango.clubmanager.domain.repository.TeamRepository
import com.ricardofrango.clubmanager.domain.service.TransactionManager
import com.ricardofrango.clubmanager.exception.ValidationException
import com.ricardofrango.clubmanager.model.ClubRole
import com.ricardofrango.clubmanager.model.CreateTeamRequest
import com.ricardofrango.clubmanager.model.Team
import kotlin.uuid.Uuid

class CreateTeamUseCase(
    private val teamRepository: TeamRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, request: CreateTeamRequest): Team = transactionManager.runInTransaction {
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

        // A team's age group must actually belong to the club and season it's being created under --
        // otherwise a team could reference an age group from a completely different season/club.
        val ageGroup = ageGroupRepository.getAgeGroupById(request.ageGroupId)
            ?: throw ValidationException("Age group not found")
        if (ageGroup.clubId != request.clubId) {
            throw ValidationException("Age group does not belong to this club")
        }
        if (ageGroup.seasonId != request.seasonId) {
            throw ValidationException("Age group does not belong to the given season")
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

        teamRepository.createTeam(team)
    }
}
