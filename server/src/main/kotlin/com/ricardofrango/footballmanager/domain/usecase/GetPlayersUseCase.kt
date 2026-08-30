package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.model.ClubRole
import com.ricardofrango.footballmanager.model.Player

class GetPlayersUseCase(
    private val playerRepository: PlayerRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase
) {
    suspend operator fun invoke(
        userId: String,
        clubId: String,
        teamId: String? = null
    ): List<Player> {
        // 1. Check permissions first (Member of club)
        checkUserPermissionUseCase(userId, clubId, minClubRole = ClubRole.MEMBER, teamId = teamId)

        return if (teamId != null) {
            playerRepository.getPlayersByTeam(teamId)
        } else {
            playerRepository.getPlayersByClub(clubId)
        }
    }
}
