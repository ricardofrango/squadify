package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.UserRepository
import com.sportssoftw.squadcall.domain.service.TransactionManager
import com.sportssoftw.squadcall.exception.NotFoundException
import com.sportssoftw.squadcall.exception.ValidationException
import com.sportssoftw.squadcall.model.UpdateProfileRequest
import com.sportssoftw.squadcall.model.User

/**
 * Self-service profile edit -- always acts on the caller's own row (`userId` comes from the JWT via
 * `call.userId`, never a path parameter), so there's no permission check beyond being authenticated at
 * all. Email/status/isPlatformAdmin are deliberately not editable here.
 */
class UpdateProfileUseCase(
    private val userRepository: UserRepository,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, request: UpdateProfileRequest): User = transactionManager.runInTransaction {
        if (request.firstName.isBlank() || request.lastName.isBlank()) {
            throw ValidationException("First and last name are required")
        }

        val updated = userRepository.updateProfile(
            userId = userId,
            firstName = request.firstName.trim(),
            lastName = request.lastName.trim(),
            phone = request.phone?.trim()?.takeIf { it.isNotBlank() }
        )
        if (!updated) throw NotFoundException("User not found")

        userRepository.findById(userId) ?: throw NotFoundException("User not found")
    }
}
