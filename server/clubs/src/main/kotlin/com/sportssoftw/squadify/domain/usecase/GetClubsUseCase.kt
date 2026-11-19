package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.ClubRepository
import com.sportssoftw.squadify.domain.repository.UserRepository
import com.sportssoftw.squadify.domain.service.TransactionManager
import com.sportssoftw.squadify.model.Club

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
