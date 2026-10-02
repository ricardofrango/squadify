package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.model.UpdateAgeGroupRequest

class UpdateAgeGroupUseCase(private val ageGroupRepository: AgeGroupRepository) {
    suspend operator fun invoke(ageGroupId: String, request: UpdateAgeGroupRequest) = ageGroupRepository.updateAgeGroup(ageGroupId, request)
}
