package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.AgeGroupRepository
import com.sportssoftw.squadcall.model.AgeGroupSeasonRecord

class GetAgeGroupSeasonRecordUseCase(
    private val ageGroupRepository: AgeGroupRepository
) {
    suspend operator fun invoke(ageGroupId: String): AgeGroupSeasonRecord = ageGroupRepository.getAgeGroupSeasonRecord(ageGroupId)
}
