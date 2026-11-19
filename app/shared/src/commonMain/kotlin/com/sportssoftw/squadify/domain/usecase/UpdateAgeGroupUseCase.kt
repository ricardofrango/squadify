package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.AgeGroupRepository
import com.sportssoftw.squadify.model.UpdateAgeGroupRequest

class UpdateAgeGroupUseCase(private val ageGroupRepository: AgeGroupRepository) {
    suspend operator fun invoke(ageGroupId: String, request: UpdateAgeGroupRequest) = ageGroupRepository.updateAgeGroup(ageGroupId, request)
}
