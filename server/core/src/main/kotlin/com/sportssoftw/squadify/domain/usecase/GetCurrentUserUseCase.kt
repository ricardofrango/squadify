package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.UserRepository
import com.sportssoftw.squadify.domain.service.TransactionManager
import com.sportssoftw.squadify.exception.NotFoundException
import com.sportssoftw.squadify.model.User

class GetCurrentUserUseCase(
    private val userRepository: UserRepository,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String): User = transactionManager.runInTransaction {
        userRepository.findById(userId) ?: throw NotFoundException("User not found")
    }
}
