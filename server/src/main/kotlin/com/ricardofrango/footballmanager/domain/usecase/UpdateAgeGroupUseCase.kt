package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.AgeGroupRepository
import com.ricardofrango.footballmanager.exception.NotFoundException
import com.ricardofrango.footballmanager.model.ClubRole
import com.ricardofrango.footballmanager.model.UpdateAgeGroupRequest

class UpdateAgeGroupUseCase(
    private val ageGroupRepository: AgeGroupRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase
) {
    suspend operator fun invoke(
        userId: String,
        ageGroupId: String,
        request: UpdateAgeGroupRequest
    ): Boolean {
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
            birthYear = request.birthYear ?: existingAgeGroup.birthYear
        )

        return ageGroupRepository.updateAgeGroup(updatedAgeGroup)
    }
}
