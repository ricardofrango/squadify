package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.access.AccessPolicy
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.model.AgeGroupPlayer

class GetAgeGroupPlayersByClubUseCase(
    private val playerRepository: PlayerRepository,
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, clubId: String): List<AgeGroupPlayer> = transactionManager.runInTransaction {
        accessPolicy.requireMember(userId, clubId)
        playerRepository.getAgeGroupPlayersByClub(clubId)
    }
}
