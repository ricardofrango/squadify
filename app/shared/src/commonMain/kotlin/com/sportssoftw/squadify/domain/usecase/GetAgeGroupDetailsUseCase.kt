package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.AgeGroupRepository
import com.sportssoftw.squadify.model.AgeGroupDetails

class GetAgeGroupDetailsUseCase(
    private val ageGroupRepository: AgeGroupRepository
) {
    suspend operator fun invoke(ageGroupId: String): AgeGroupDetails {
        return ageGroupRepository.getAgeGroupDetails(ageGroupId)
    }
}
