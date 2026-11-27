package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.AgeGroupRepository

class GetAgeGroupStaffUseCase(
    private val ageGroupRepository: AgeGroupRepository
) {
    suspend operator fun invoke(ageGroupId: String) = ageGroupRepository.getAgeGroupStaff(ageGroupId)
}
