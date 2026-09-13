package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.CreateAgeGroupsRequest

class CreateAgeGroupsUseCase(
    private val ageGroupRepository: AgeGroupRepository
) {
    suspend operator fun invoke(request: CreateAgeGroupsRequest): List<AgeGroup> {
        return ageGroupRepository.createAgeGroups(request)
    }
}
