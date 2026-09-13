package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException

class CheckUserIsAdminUseCase(
    private val userRepository: UserRepository,
    private val transactionManager: TransactionManager
) {
    /**
     * Checks if a user is admin in the club.
     * 
     * @param userId The ID of the user performing the action.
     * @return true if the user is admin, false otherwise.
     */
    suspend operator fun invoke(userId: String): Boolean = transactionManager.runInTransaction {
        val user = userRepository.findById(userId) ?: throw ForbiddenException("User not found")
        user.isPlatformAdmin
    }
}
