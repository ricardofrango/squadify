package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.TeamRepository

/** Deletes a team with all its matches, played ones too. */
class DeleteTeamUseCase(private val teamRepository: TeamRepository) {
    suspend operator fun invoke(teamId: String) = teamRepository.deleteTeam(teamId)
}
