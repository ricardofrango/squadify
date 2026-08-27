package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.SeasonRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.exception.ForbiddenException
import com.ricardofrango.footballmanager.exception.ValidationException
import com.ricardofrango.footballmanager.model.Role

class UpdateSeasonStatusUseCase(
    private val seasonRepository: SeasonRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(userId: String, clubId: String, seasonId: String, active: Boolean): Boolean {
        // 1. Check permissions
        val userRole = userRepository.getUserRoleInClub(userId, clubId)
        if (userRole != Role.CLUB_ADMIN && userRole != Role.COORDINATOR) {
            val user = userRepository.findById(userId)
            if (user?.isPlatformAdmin != true) {
                throw ForbiddenException("Only admins or coordinators can change season status")
            }
        }

        // 2. Verify season exists and belongs to the club
        val season = seasonRepository.getSeasonById(seasonId)
            ?: throw ValidationException("Season not found")
        
        if (season.clubId != clubId) {
            throw ForbiddenException("Season does not belong to this club")
        }

        return seasonRepository.updateActiveStatus(seasonId, active)
    }
}
