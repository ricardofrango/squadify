package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.access.AccessPolicy
import com.sportssoftw.squadcall.domain.repository.PlayerRepository
import com.sportssoftw.squadcall.domain.service.TransactionManager
import com.sportssoftw.squadcall.model.AgeGroupPlayer

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
