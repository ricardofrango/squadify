package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.model.Role

class CheckTeamPermissionUseCase(private val userRepository: UserRepository) {
    suspend operator fun invoke(userId: String, clubId: String, teamId: String, requiredRoles: List<Role>): Boolean {
        val user = userRepository.findById(userId) ?: return false
        if (user.isPlatformAdmin) return true

        // First check if user is Club Admin or Coordinator (they have power over all teams)
        val clubRole = userRepository.getUserRoleInClub(userId, clubId)
        if (clubRole == Role.CLUB_ADMIN || clubRole == Role.COORDINATOR) return true

        // If not, check if they are a coach in this specific team
        // Note: For MVP, we can simplify this. In a real scenario, we'd check TeamStaff table.
        // Let's assume for now that if they have the COACH role in the club, they might be restricted to specific teams.
        // We will need a TeamRepository to check if the user is assigned to the team.
        
        return false // Placeholder until TeamStaff is fully implemented
    }
}
