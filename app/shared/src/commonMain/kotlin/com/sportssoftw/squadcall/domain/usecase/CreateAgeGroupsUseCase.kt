package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.AgeGroupRepository
import com.sportssoftw.squadcall.model.AgeGroup
import com.sportssoftw.squadcall.model.CreateAgeGroupsRequest

class CreateAgeGroupsUseCase(
    private val ageGroupRepository: AgeGroupRepository
) {
    suspend operator fun invoke(request: CreateAgeGroupsRequest): List<AgeGroup> {
        return ageGroupRepository.createAgeGroups(request)
    }
}
