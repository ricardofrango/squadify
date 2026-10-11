package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.CurrentPasswordGuard
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.ChangePasswordRequest
import org.mindrot.jbcrypt.BCrypt

/**
 * Self-service password change -- requires the caller's current password (checked by
 * [CurrentPasswordGuard]: a wrong one is a 400, and the third in a row signs the user out), so a
 * stolen/leaked access token alone can't lock the real account owner out by changing their password.
 */
class ChangePasswordUseCase(
    private val userRepository: UserRepository,
    private val transactionManager: TransactionManager,
    private val currentPasswordGuard: CurrentPasswordGuard
) {
    suspend operator fun invoke(userId: String, request: ChangePasswordRequest): Boolean {
        if (request.newPassword.length < 8) {
            throw ValidationException("New password must be at least 8 characters")
        }
        currentPasswordGuard.verify(userId, request.currentPassword)

        return transactionManager.runInTransaction {
            val newHash = BCrypt.hashpw(request.newPassword, BCrypt.gensalt())
            userRepository.updatePasswordHash(userId, newHash)
        }
    }
}
