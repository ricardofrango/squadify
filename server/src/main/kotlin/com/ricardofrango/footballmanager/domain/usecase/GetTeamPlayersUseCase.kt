package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.exception.ValidationException
import com.ricardofrango.footballmanager.model.Player
import com.ricardofrango.footballmanager.model.Role

class GetTeamPlayersUseCase(
    private val playerRepository: PlayerRepository,
    private val teamRepository: TeamRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase
) {
    suspend operator fun invoke(userId: String, teamId: String): List<Player> {
        val team = teamRepository.getTeamById(teamId) ?: throw ValidationException("Team not found")

        // 1. Check permissions first (Fail fast)
        checkUserPermissionUseCase(userId, team.clubId, Role.PLAYER, teamId)

        return playerRepository.getPlayersByTeam(teamId)
    }
}
