package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.model.TeamDetails

class GetTeamDetailsUseCase(
    private val teamRepository: TeamRepository
) {
    suspend operator fun invoke(teamId: String): TeamDetails {
        return teamRepository.getTeamDetails(teamId)
    }
}
