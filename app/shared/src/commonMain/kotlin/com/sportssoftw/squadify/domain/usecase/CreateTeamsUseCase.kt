package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.TeamRepository
import com.sportssoftw.squadify.model.CreateTeamsRequest
import com.sportssoftw.squadify.model.Team

class CreateTeamsUseCase(
    private val teamRepository: TeamRepository
) {
    suspend operator fun invoke(request: CreateTeamsRequest): List<Team> {
        return teamRepository.createTeams(request)
    }
}
