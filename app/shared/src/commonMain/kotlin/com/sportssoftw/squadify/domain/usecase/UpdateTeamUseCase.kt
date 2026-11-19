package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.TeamRepository
import com.sportssoftw.squadify.model.UpdateTeamRequest

class UpdateTeamUseCase(private val teamRepository: TeamRepository) {
    suspend operator fun invoke(teamId: String, request: UpdateTeamRequest) = teamRepository.updateTeam(teamId, request)
}
