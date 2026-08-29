package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.exception.ForbiddenException

class CheckUserIsAdminUseCase(
    private val userRepository: UserRepository,
) {
    /**
     * Checks if a user is admin in the club.
     * 
     * @param userId The ID of the user performing the action.
     * @return true if the user is admin, false otherwise.
     */
    suspend operator fun invoke(userId: String): Boolean {
        val user = userRepository.findById(userId) ?: throw ForbiddenException("User not found")
        return user.isPlatformAdmin
    }
}
