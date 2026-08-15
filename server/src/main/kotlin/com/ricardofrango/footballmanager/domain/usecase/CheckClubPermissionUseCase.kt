package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.model.Role

class CheckClubPermissionUseCase(private val userRepository: UserRepository) {
    suspend operator fun invoke(userId: String, clubId: String, requiredRoles: List<Role>): Boolean {
        val userRole = userRepository.getUserRoleInClub(userId, clubId) ?: return false
        return requiredRoles.contains(userRole)
    }
}
