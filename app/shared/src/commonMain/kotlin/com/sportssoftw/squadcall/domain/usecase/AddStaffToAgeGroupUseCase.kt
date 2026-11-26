package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.AgeGroupRepository
import com.sportssoftw.squadcall.model.AddStaffToAgeGroupRequest

class AddStaffToAgeGroupUseCase(
    private val ageGroupRepository: AgeGroupRepository
) {
    suspend operator fun invoke(ageGroupId: String, request: AddStaffToAgeGroupRequest) =
        ageGroupRepository.addStaffToAgeGroup(ageGroupId, request)
}
