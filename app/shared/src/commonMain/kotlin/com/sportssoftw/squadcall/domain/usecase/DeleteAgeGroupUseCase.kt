package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.AgeGroupRepository

/** Deletes an age group for good (`DELETE /v1/age-groups/{id}`), with everything in it. */
class DeleteAgeGroupUseCase(private val ageGroupRepository: AgeGroupRepository) {
    suspend operator fun invoke(ageGroupId: String) = ageGroupRepository.deleteAgeGroup(ageGroupId)
}
