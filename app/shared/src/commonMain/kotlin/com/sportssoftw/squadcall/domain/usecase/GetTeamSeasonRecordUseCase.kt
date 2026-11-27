package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.TeamRepository
import com.sportssoftw.squadcall.model.TeamSeasonRecord

class GetTeamSeasonRecordUseCase(
    private val teamRepository: TeamRepository
) {
    suspend operator fun invoke(teamId: String): TeamSeasonRecord = teamRepository.getTeamSeasonRecord(teamId)
}
