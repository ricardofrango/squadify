package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.AgeGroupRepository
import com.sportssoftw.squadify.model.AddStaffToAgeGroupRequest

class AddStaffToAgeGroupUseCase(
    private val ageGroupRepository: AgeGroupRepository
) {
    suspend operator fun invoke(ageGroupId: String, request: AddStaffToAgeGroupRequest) =
        ageGroupRepository.addStaffToAgeGroup(ageGroupId, request)
}
