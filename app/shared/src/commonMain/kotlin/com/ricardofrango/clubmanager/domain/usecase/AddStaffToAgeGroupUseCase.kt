package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.AgeGroupRepository
import com.ricardofrango.clubmanager.model.AddStaffToAgeGroupRequest

class AddStaffToAgeGroupUseCase(
    private val ageGroupRepository: AgeGroupRepository
) {
    suspend operator fun invoke(ageGroupId: String, request: AddStaffToAgeGroupRequest) =
        ageGroupRepository.addStaffToAgeGroup(ageGroupId, request)
}
