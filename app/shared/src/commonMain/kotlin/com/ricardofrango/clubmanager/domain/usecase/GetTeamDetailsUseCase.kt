package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.TeamRepository
import com.ricardofrango.clubmanager.model.TeamDetails

class GetTeamDetailsUseCase(
    private val teamRepository: TeamRepository
) {
    suspend operator fun invoke(teamId: String): TeamDetails {
        return teamRepository.getTeamDetails(teamId)
    }
}
