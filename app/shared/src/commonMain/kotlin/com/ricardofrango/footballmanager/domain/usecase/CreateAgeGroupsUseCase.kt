package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.AgeGroupRepository
import com.ricardofrango.footballmanager.model.AgeGroup
import com.ricardofrango.footballmanager.model.CreateAgeGroupsRequest

class CreateAgeGroupsUseCase(
    private val ageGroupRepository: AgeGroupRepository
) {
    suspend operator fun invoke(request: CreateAgeGroupsRequest): List<AgeGroup> {
        return ageGroupRepository.createAgeGroups(request)
    }
}
