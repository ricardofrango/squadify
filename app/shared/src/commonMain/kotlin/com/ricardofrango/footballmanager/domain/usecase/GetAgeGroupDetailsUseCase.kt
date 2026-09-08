package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.AgeGroupRepository
import com.ricardofrango.footballmanager.model.AgeGroupDetails

class GetAgeGroupDetailsUseCase(
    private val ageGroupRepository: AgeGroupRepository
) {
    suspend operator fun invoke(ageGroupId: String): AgeGroupDetails {
        return ageGroupRepository.getAgeGroupDetails(ageGroupId)
    }
}
