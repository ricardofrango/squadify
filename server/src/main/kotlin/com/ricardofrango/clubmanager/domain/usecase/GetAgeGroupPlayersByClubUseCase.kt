package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.PlayerRepository
import com.ricardofrango.clubmanager.domain.service.TransactionManager
import com.ricardofrango.clubmanager.model.AgeGroupPlayer
import com.ricardofrango.clubmanager.model.ClubRole

class GetAgeGroupPlayersByClubUseCase(
    private val playerRepository: PlayerRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, clubId: String): List<AgeGroupPlayer> = transactionManager.runInTransaction {
        checkUserPermissionUseCase(userId, clubId, minClubRole = ClubRole.MEMBER)
        playerRepository.getAgeGroupPlayersByClub(clubId)
    }
}
