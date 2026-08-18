package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.model.Team

class GetTeamsUseCase(private val teamRepository: TeamRepository) {
    suspend operator fun invoke(clubId: String): List<Team> {
        return teamRepository.getTeamsByClub(clubId)
    }
}
