package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.AgeGroupRepository
import com.ricardofrango.footballmanager.model.AgeGroup

class GetAgeGroupsUseCase(private val ageGroupRepository: AgeGroupRepository) {
    suspend operator fun invoke(clubId: String): List<AgeGroup> {
        return ageGroupRepository.getAgeGroupsByClub(clubId)
    }
}
