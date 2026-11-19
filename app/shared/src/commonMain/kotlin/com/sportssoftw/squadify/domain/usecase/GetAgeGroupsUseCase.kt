package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.AgeGroupRepository
import com.sportssoftw.squadify.model.AgeGroup

class GetAgeGroupsUseCase(
    private val ageGroupRepository: AgeGroupRepository
) {
    suspend operator fun invoke(clubId: String): List<AgeGroup> {
        return ageGroupRepository.getAgeGroups(clubId)
    }
}
