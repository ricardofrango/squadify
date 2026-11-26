package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.TeamRepository
import com.sportssoftw.squadcall.model.UpdateTeamRequest

class UpdateTeamUseCase(private val teamRepository: TeamRepository) {
    suspend operator fun invoke(teamId: String, request: UpdateTeamRequest) = teamRepository.updateTeam(teamId, request)
}
