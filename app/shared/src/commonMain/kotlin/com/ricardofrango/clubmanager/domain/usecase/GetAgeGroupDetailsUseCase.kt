package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.AgeGroupRepository
import com.ricardofrango.clubmanager.model.AgeGroupDetails

class GetAgeGroupDetailsUseCase(
    private val ageGroupRepository: AgeGroupRepository
) {
    suspend operator fun invoke(ageGroupId: String): AgeGroupDetails {
        return ageGroupRepository.getAgeGroupDetails(ageGroupId)
    }
}
