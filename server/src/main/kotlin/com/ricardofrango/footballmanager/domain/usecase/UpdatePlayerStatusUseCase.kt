package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.exception.ForbiddenException
import com.ricardofrango.footballmanager.exception.ValidationException
import com.ricardofrango.footballmanager.model.Role

class UpdatePlayerStatusUseCase(
    private val playerRepository: PlayerRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(userId: String, clubId: String, playerId: String, active: Boolean): Boolean {
        // 1. Check permissions
        val userRole = userRepository.getUserRoleInClub(userId, clubId)
        if (userRole != Role.CLUB_ADMIN && userRole != Role.COORDINATOR) {
            val user = userRepository.findById(userId)
            if (user?.isPlatformAdmin != true) {
                throw ForbiddenException("Only admins or coordinators can change player status")
            }
        }

        // 2. Verify player exists and belongs to the club
        val player = playerRepository.getPlayerById(playerId)
            ?: throw ValidationException("Player not found")
        
        if (player.clubId != clubId) {
            throw ForbiddenException("Player does not belong to this club")
        }

        return playerRepository.updateActiveStatus(playerId, active)
    }
}
