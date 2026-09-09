package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.AgeGroupRepository
import com.ricardofrango.clubmanager.domain.repository.TeamRepository
import com.ricardofrango.clubmanager.domain.service.TransactionManager
import com.ricardofrango.clubmanager.exception.ValidationException
import com.ricardofrango.clubmanager.model.ClubRole
import com.ricardofrango.clubmanager.model.CreateTeamsRequest
import com.ricardofrango.clubmanager.model.Team
import kotlin.uuid.Uuid

/**
 * Batch counterpart of [CreateTeamUseCase]: creates every team in [CreateTeamsRequest.items] under
 * the one season/age group, inside a single transaction so the whole batch either lands or none of
 * it does (mirrors [CreateAgeGroupsUseCase]). Same COORDINATOR+ gate and same age-group-belongs-to-
 * club-and-season validation as the single-team case.
 */
class CreateTeamsUseCase(
    private val teamRepository: TeamRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, request: CreateTeamsRequest): List<Team> = transactionManager.runInTransaction {
        checkUserPermissionUseCase(
            userId = userId,
            clubId = request.clubId,
            minClubRole = ClubRole.COORDINATOR,
            errorMessage = "Only admins or coordinators can create teams"
        )

        if (request.items.isEmpty()) {
            throw ValidationException("At least one team must be provided")
        }

        // The age group must actually belong to the club and season the teams are being created
        // under -- otherwise a team could reference an age group from a different season/club.
        val ageGroup = ageGroupRepository.getAgeGroupById(request.ageGroupId)
            ?: throw ValidationException("Age group not found")
        if (ageGroup.clubId != request.clubId) {
            throw ValidationException("Age group does not belong to this club")
        }
        if (ageGroup.seasonId != request.seasonId) {
            throw ValidationException("Age group does not belong to the given season")
        }

        val newTeams = request.items.map { item ->
            if (item.name.isBlank()) {
                throw ValidationException("Team name cannot be empty")
            }
            Team(
                id = Uuid.random().toString(),
                clubId = request.clubId,
                ageGroupId = request.ageGroupId,
                seasonId = request.seasonId,
                name = item.name,
                code = item.code,
                active = true
            )
        }

        teamRepository.createTeams(newTeams)
    }
}
