package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.TeamRepository
import com.sportssoftw.squadcall.model.TeamDetails

class GetTeamDetailsUseCase(
    private val teamRepository: TeamRepository
) {
    suspend operator fun invoke(teamId: String): TeamDetails {
        return teamRepository.getTeamDetails(teamId)
    }
}
