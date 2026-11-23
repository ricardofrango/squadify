package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.AgeGroupRepository
import com.sportssoftw.squadify.model.AgeGroupSeasonRecord

class GetAgeGroupSeasonRecordUseCase(
    private val ageGroupRepository: AgeGroupRepository
) {
    suspend operator fun invoke(ageGroupId: String): AgeGroupSeasonRecord = ageGroupRepository.getAgeGroupSeasonRecord(ageGroupId)
}
