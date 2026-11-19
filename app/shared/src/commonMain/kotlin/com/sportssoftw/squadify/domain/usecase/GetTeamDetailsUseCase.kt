package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.TeamRepository
import com.sportssoftw.squadify.model.TeamDetails

class GetTeamDetailsUseCase(
    private val teamRepository: TeamRepository
) {
    suspend operator fun invoke(teamId: String): TeamDetails {
        return teamRepository.getTeamDetails(teamId)
    }
}
