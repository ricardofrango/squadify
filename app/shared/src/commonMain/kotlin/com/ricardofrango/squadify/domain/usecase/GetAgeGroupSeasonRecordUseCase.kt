package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.model.AgeGroupSeasonRecord

class GetAgeGroupSeasonRecordUseCase(
    private val ageGroupRepository: AgeGroupRepository
) {
    suspend operator fun invoke(ageGroupId: String): AgeGroupSeasonRecord = ageGroupRepository.getAgeGroupSeasonRecord(ageGroupId)
}
