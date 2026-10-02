package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.model.UpdateTeamRequest

class UpdateTeamUseCase(private val teamRepository: TeamRepository) {
    suspend operator fun invoke(teamId: String, request: UpdateTeamRequest) = teamRepository.updateTeam(teamId, request)
}
