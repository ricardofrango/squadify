package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.model.CreateTeamsRequest
import com.ricardofrango.footballmanager.model.Team

class CreateTeamsUseCase(
    private val teamRepository: TeamRepository
) {
    suspend operator fun invoke(request: CreateTeamsRequest): List<Team> {
        return teamRepository.createTeams(request)
    }
}
