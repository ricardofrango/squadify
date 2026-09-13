package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.model.AgeGroupDetails

class GetAgeGroupDetailsUseCase(
    private val ageGroupRepository: AgeGroupRepository
) {
    suspend operator fun invoke(ageGroupId: String): AgeGroupDetails {
        return ageGroupRepository.getAgeGroupDetails(ageGroupId)
    }
}
