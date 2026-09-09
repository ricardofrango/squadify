package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.AgeGroupRepository
import com.ricardofrango.clubmanager.model.AgeGroup
import com.ricardofrango.clubmanager.model.CreateAgeGroupsRequest

class CreateAgeGroupsUseCase(
    private val ageGroupRepository: AgeGroupRepository
) {
    suspend operator fun invoke(request: CreateAgeGroupsRequest): List<AgeGroup> {
        return ageGroupRepository.createAgeGroups(request)
    }
}
