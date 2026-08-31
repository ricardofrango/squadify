package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.AgeGroupRepository
import com.ricardofrango.footballmanager.domain.service.TransactionManager
import com.ricardofrango.footballmanager.exception.ConflictException
import com.ricardofrango.footballmanager.exception.ValidationException
import com.ricardofrango.footballmanager.model.AgeGroup
import com.ricardofrango.footballmanager.model.ClubRole
import com.ricardofrango.footballmanager.model.CreateAgeGroupRequest
import kotlin.uuid.Uuid

class CreateAgeGroupUseCase(
    private val ageGroupRepository: AgeGroupRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, request: CreateAgeGroupRequest): AgeGroup = transactionManager.runInTransaction {
        checkUserPermissionUseCase(
            userId = userId,
            clubId = request.clubId,
            minClubRole = ClubRole.COORDINATOR,
            errorMessage = "Only admins or coordinators can create age groups"
        )

        if (request.name.isBlank()) {
            throw ValidationException("Age group name cannot be empty")
        }

        // Check for duplicate age group
        val existing =
            ageGroupRepository.findByDetails(request.clubId, request.seasonId, request.birthYear)
        if (existing != null) {
            throw ConflictException("An age group for this birth year already exists in this season")
        }

        val ageGroup = AgeGroup(
            id = Uuid.random().toString(),
            clubId = request.clubId,
            name = request.name,
            seasonId = request.seasonId,
            birthYear = request.birthYear
        )

        ageGroupRepository.createAgeGroup(ageGroup)
    }
}
