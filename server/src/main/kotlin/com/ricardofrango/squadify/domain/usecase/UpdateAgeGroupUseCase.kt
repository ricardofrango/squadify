package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.UpdateAgeGroupRequest

class UpdateAgeGroupUseCase(
    private val ageGroupRepository: AgeGroupRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(
        userId: String,
        ageGroupId: String,
        request: UpdateAgeGroupRequest
    ): Boolean = transactionManager.runInTransaction {
        val existingAgeGroup = ageGroupRepository.getAgeGroupById(ageGroupId)
            ?: throw NotFoundException("Age group not found")

        checkUserPermissionUseCase(
            userId = userId,
            clubId = existingAgeGroup.clubId,
            minClubRole = ClubRole.COORDINATOR,
            errorMessage = "Only admins or coordinators can update age groups"
        )

        val updatedAgeGroup = existingAgeGroup.copy(
            name = request.name ?: existingAgeGroup.name,
            startYear = request.startYear ?: existingAgeGroup.startYear,
            endYear = request.endYear ?: existingAgeGroup.endYear
        )

        if (updatedAgeGroup.startYear < updatedAgeGroup.endYear) {
            throw ValidationException("Start year cannot be before end year")
        }

        ageGroupRepository.updateAgeGroup(updatedAgeGroup)
    }
}
