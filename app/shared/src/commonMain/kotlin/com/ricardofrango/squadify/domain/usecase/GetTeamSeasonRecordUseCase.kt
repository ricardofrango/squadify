package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.model.TeamSeasonRecord

class GetTeamSeasonRecordUseCase(
    private val teamRepository: TeamRepository
) {
    suspend operator fun invoke(teamId: String): TeamSeasonRecord = teamRepository.getTeamSeasonRecord(teamId)
}
