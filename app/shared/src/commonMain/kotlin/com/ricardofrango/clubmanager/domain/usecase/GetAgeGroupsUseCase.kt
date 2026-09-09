package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.AgeGroupRepository
import com.ricardofrango.clubmanager.model.AgeGroup

class GetAgeGroupsUseCase(
    private val ageGroupRepository: AgeGroupRepository
) {
    suspend operator fun invoke(clubId: String): List<AgeGroup> {
        return ageGroupRepository.getAgeGroups(clubId)
    }
}
