package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.AgeGroupRepository
import com.sportssoftw.squadcall.model.UpdateAgeGroupRequest

class UpdateAgeGroupUseCase(private val ageGroupRepository: AgeGroupRepository) {
    suspend operator fun invoke(ageGroupId: String, request: UpdateAgeGroupRequest) = ageGroupRepository.updateAgeGroup(ageGroupId, request)
}
