package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.domain.service.TransactionManager
import com.ricardofrango.footballmanager.model.ClubRole
import com.ricardofrango.footballmanager.model.TeamPlayer

class GetTeamPlayersByClubUseCase(
    private val playerRepository: PlayerRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, clubId: String): List<TeamPlayer> = transactionManager.runInTransaction {
        checkUserPermissionUseCase(userId, clubId, minClubRole = ClubRole.MEMBER)
        playerRepository.getTeamPlayersByClub(clubId)
    }
}
