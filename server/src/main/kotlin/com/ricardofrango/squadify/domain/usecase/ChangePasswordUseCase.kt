package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.UnauthorizedException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.ChangePasswordRequest
import org.mindrot.jbcrypt.BCrypt

/**
 * Self-service password change -- requires the caller's current password to verify (same
 * `BCrypt.checkpw` gate `LoginUseCase` uses), so a stolen/leaked access token alone can't lock the
 * real account owner out by changing their password.
 */
class ChangePasswordUseCase(
    private val userRepository: UserRepository,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, request: ChangePasswordRequest): Boolean = transactionManager.runInTransaction {
        if (request.newPassword.length < 8) {
            throw ValidationException("New password must be at least 8 characters")
        }

        val user = userRepository.findById(userId) ?: throw NotFoundException("User not found")
        val currentHash = userRepository.findPasswordHashByEmail(user.email) ?: throw NotFoundException("User not found")

        if (!BCrypt.checkpw(request.currentPassword, currentHash)) {
            throw UnauthorizedException("Current password is incorrect")
        }

        val newHash = BCrypt.hashpw(request.newPassword, BCrypt.gensalt())
        userRepository.updatePasswordHash(userId, newHash)
    }
}
