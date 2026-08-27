package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.exception.ValidationException
import com.ricardofrango.footballmanager.model.TeamDetails

class GetTeamDetailsUseCase(
    private val teamRepository: TeamRepository,
    private val playerRepository: PlayerRepository
) {
    suspend operator fun invoke(teamId: String): TeamDetails {
        val team = teamRepository.getTeamById(teamId)
            ?: throw ValidationException("Team not found")
        
        val players = playerRepository.getPlayersByTeam(teamId)
        val staff = teamRepository.getTeamStaff(teamId)
        
        return TeamDetails(
            team = team,
            players = players,
            staff = staff
        )
    }
}
