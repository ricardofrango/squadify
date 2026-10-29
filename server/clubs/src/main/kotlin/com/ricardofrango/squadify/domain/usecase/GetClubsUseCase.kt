package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.model.Club

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
