package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.AgeGroupRepository
import com.sportssoftw.squadcall.model.AgeGroupDetails

class GetAgeGroupDetailsUseCase(
    private val ageGroupRepository: AgeGroupRepository
) {
    suspend operator fun invoke(ageGroupId: String): AgeGroupDetails {
        return ageGroupRepository.getAgeGroupDetails(ageGroupId)
    }
}
