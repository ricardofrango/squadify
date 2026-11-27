package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.ClubRepository
import com.sportssoftw.squadcall.domain.repository.UserRepository
import com.sportssoftw.squadcall.domain.service.TransactionManager
import com.sportssoftw.squadcall.model.Club

class GetClubsUseCase(
    private val clubRepository: ClubRepository,
    private val checkUserIsAdminUseCase: CheckUserIsAdminUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String): List<Club> = transactionManager.runInTransaction {
        val isAdmin = checkUserIsAdminUseCase(userId)
        
        if (isAdmin) {
            clubRepository.getAllClubs()
        } else {
            clubRepository.getClubsByUser(userId)
        }
    }
}
