package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository

class GetAgeGroupStaffUseCase(
    private val ageGroupRepository: AgeGroupRepository
) {
    suspend operator fun invoke(ageGroupId: String) = ageGroupRepository.getAgeGroupStaff(ageGroupId)
}
