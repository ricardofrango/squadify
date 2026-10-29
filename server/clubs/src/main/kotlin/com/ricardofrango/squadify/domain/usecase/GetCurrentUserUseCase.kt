package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.User

class GetCurrentUserUseCase(
    private val userRepository: UserRepository,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String): User = transactionManager.runInTransaction {
        userRepository.findById(userId) ?: throw NotFoundException("User not found")
    }
}
