package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.TeamRepository

/** Deletes a team with all its matches, played ones too. */
class DeleteTeamUseCase(private val teamRepository: TeamRepository) {
    suspend operator fun invoke(teamId: String) = teamRepository.deleteTeam(teamId)
}
