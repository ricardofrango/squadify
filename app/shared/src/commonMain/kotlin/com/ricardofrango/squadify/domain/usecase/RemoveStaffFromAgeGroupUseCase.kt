package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository

/**
 * The coach app's owner takes someone off an age group's staff (TECHNICAL_DECISIONS §72); off their last
 * age group of the space, the server takes them out of the space too.
 */
class RemoveStaffFromAgeGroupUseCase(private val ageGroupRepository: AgeGroupRepository) {
    suspend operator fun invoke(ageGroupId: String, userId: String) = ageGroupRepository.removeStaffFromAgeGroup(ageGroupId, userId)
}
