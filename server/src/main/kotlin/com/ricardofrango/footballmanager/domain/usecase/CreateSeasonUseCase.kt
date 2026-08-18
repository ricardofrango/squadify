package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.SeasonRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.exception.ForbiddenException
import com.ricardofrango.footballmanager.exception.ValidationException
import com.ricardofrango.footballmanager.model.Role
import com.ricardofrango.footballmanager.model.Season

class CreateSeasonUseCase(
    private val seasonRepository: SeasonRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(userId: String, season: Season): Boolean {
        val userRole = userRepository.getUserRoleInClub(userId, season.clubId)
        if (userRole != Role.CLUB_ADMIN && userRole != Role.COORDINATOR) {
            throw ForbiddenException("Only admins or coordinators can create seasons")
        }

        if (season.name.isBlank()) {
            throw ValidationException("Season name cannot be empty")
        }

        return seasonRepository.createSeason(season)
    }
}
