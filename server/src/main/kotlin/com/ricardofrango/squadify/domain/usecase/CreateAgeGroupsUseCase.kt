package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ConflictException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.CreateAgeGroupsRequest
import kotlin.uuid.Uuid

class CreateAgeGroupsUseCase(
    private val ageGroupRepository: AgeGroupRepository,
    private val clubRepository: ClubRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, request: CreateAgeGroupsRequest): List<AgeGroup> = transactionManager.runInTransaction {
        checkUserPermissionUseCase(
            userId = userId,
            clubId = request.clubId,
            minClubRole = ClubRole.COORDINATOR,
            errorMessage = "Only admins or coordinators can create age groups"
        )

        if (request.items.isEmpty()) {
            throw ValidationException("At least one age group must be provided")
        }

        val club = clubRepository.getClubById(request.clubId)
            ?: throw ValidationException("Club not found")

        // Get existing age groups for this club and season to check for duplicates
        val existingAgeGroups = ageGroupRepository.findByClubAndSeason(request.clubId, request.seasonId)
        val existingYears = existingAgeGroups.map { it.birthYear }.toSet()

        val newAgeGroups = request.items.map { item ->
            if (item.name.isBlank()) {
                throw ValidationException("Age group name cannot be empty")
            }
            if (existingYears.contains(item.birthYear)) {
                throw ConflictException("An age group for birth year ${item.birthYear} already exists in this season")
            }
            if (item.teamSize != null && item.teamSize !in club.sport.teamSizes) {
                throw ValidationException("Team size ${item.teamSize} is not valid for ${club.sport}")
            }

            AgeGroup(
                id = Uuid.random().toString(),
                clubId = request.clubId,
                name = item.name,
                seasonId = request.seasonId,
                birthYear = item.birthYear,
                teamSize = item.teamSize
            )
        }

        ageGroupRepository.createAgeGroups(newAgeGroups)
    }
}
