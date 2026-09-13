package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.model.CreateTeamsRequest
import com.ricardofrango.squadify.model.Team

class CreateTeamsUseCase(
    private val teamRepository: TeamRepository
) {
    suspend operator fun invoke(request: CreateTeamsRequest): List<Team> {
        return teamRepository.createTeams(request)
    }
}
