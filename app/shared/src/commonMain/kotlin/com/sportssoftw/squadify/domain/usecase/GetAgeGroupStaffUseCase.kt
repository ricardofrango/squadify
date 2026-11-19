package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.AgeGroupRepository

class GetAgeGroupStaffUseCase(
    private val ageGroupRepository: AgeGroupRepository
) {
    suspend operator fun invoke(ageGroupId: String) = ageGroupRepository.getAgeGroupStaff(ageGroupId)
}
