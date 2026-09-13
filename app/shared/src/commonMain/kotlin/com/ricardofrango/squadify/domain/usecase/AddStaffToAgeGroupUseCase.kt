package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.model.AddStaffToAgeGroupRequest

class AddStaffToAgeGroupUseCase(
    private val ageGroupRepository: AgeGroupRepository
) {
    suspend operator fun invoke(ageGroupId: String, request: AddStaffToAgeGroupRequest) =
        ageGroupRepository.addStaffToAgeGroup(ageGroupId, request)
}
