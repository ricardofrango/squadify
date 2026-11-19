package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.AgeGroupRepository
import com.sportssoftw.squadify.model.AgeGroup
import com.sportssoftw.squadify.model.CreateAgeGroupsRequest

class CreateAgeGroupsUseCase(
    private val ageGroupRepository: AgeGroupRepository
) {
    suspend operator fun invoke(request: CreateAgeGroupsRequest): List<AgeGroup> {
        return ageGroupRepository.createAgeGroups(request)
    }
}
