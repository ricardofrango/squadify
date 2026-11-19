package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.TeamRepository
import com.sportssoftw.squadify.model.TeamSeasonRecord

class GetTeamSeasonRecordUseCase(
    private val teamRepository: TeamRepository
) {
    suspend operator fun invoke(teamId: String): TeamSeasonRecord = teamRepository.getTeamSeasonRecord(teamId)
}
