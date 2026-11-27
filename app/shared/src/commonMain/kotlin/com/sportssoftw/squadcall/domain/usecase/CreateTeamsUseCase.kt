package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.TeamRepository
import com.sportssoftw.squadcall.model.CreateTeamsRequest
import com.sportssoftw.squadcall.model.Team

class CreateTeamsUseCase(
    private val teamRepository: TeamRepository
) {
    suspend operator fun invoke(request: CreateTeamsRequest): List<Team> {
        return teamRepository.createTeams(request)
    }
}
