package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.AgeGroupRepository
import com.ricardofrango.clubmanager.domain.service.TransactionManager
import com.ricardofrango.clubmanager.exception.ConflictException
import com.ricardofrango.clubmanager.exception.ValidationException
import com.ricardofrango.clubmanager.model.AgeGroup
import com.ricardofrango.clubmanager.model.ClubRole
import com.ricardofrango.clubmanager.model.CreateAgeGroupsRequest
import kotlin.uuid.Uuid

class CreateAgeGroupsUseCase(
    private val ageGroupRepository: AgeGroupRepository,
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
            
            AgeGroup(
                id = Uuid.random().toString(),
                clubId = request.clubId,
                name = item.name,
                seasonId = request.seasonId,
                birthYear = item.birthYear
            )
        }

        ageGroupRepository.createAgeGroups(newAgeGroups)
    }
}
