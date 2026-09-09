package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.TeamRepository
import com.ricardofrango.clubmanager.model.CreateTeamsRequest
import com.ricardofrango.clubmanager.model.Team

class CreateTeamsUseCase(
    private val teamRepository: TeamRepository
) {
    suspend operator fun invoke(request: CreateTeamsRequest): List<Team> {
        return teamRepository.createTeams(request)
    }
}
