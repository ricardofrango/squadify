package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.model.Player
import com.ricardofrango.footballmanager.model.Role

class GetPlayersUseCase(
    private val playerRepository: PlayerRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase
) {
    suspend operator fun invoke(
        userId: String,
        clubId: String,
        teamId: String? = null
    ): List<Player> {
        // 1. Check permissions first (Fail fast)
        checkUserPermissionUseCase(userId, clubId, Role.PLAYER, teamId)

        return if (teamId != null) {
            playerRepository.getPlayersByTeam(teamId)
        } else {
            playerRepository.getPlayersByClub(clubId)
        }
    }
}
