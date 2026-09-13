package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.model.AgeGroupPlayer
import com.ricardofrango.squadify.model.ClubRole

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
