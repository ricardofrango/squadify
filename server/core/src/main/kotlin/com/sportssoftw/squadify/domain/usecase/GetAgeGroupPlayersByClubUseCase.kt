package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.access.AccessPolicy
import com.sportssoftw.squadify.domain.repository.PlayerRepository
import com.sportssoftw.squadify.domain.service.TransactionManager
import com.sportssoftw.squadify.model.AgeGroupPlayer

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
