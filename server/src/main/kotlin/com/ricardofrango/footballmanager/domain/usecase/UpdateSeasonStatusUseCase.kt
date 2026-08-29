package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.SeasonRepository
import com.ricardofrango.footballmanager.exception.ForbiddenException
import com.ricardofrango.footballmanager.exception.ValidationException
import com.ricardofrango.footballmanager.model.Role

class UpdateSeasonStatusUseCase(
    private val seasonRepository: SeasonRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase
) {
    suspend operator fun invoke(
        userId: String,
        clubId: String,
        seasonId: String,
        active: Boolean
    ): Boolean {
        // 1. Check permissions
        checkUserPermissionUseCase(
            userId = userId,
            clubId = clubId,
            minRole = Role.COORDINATOR,
            errorMessage = "Only admins or coordinators can change season status"
        )

        // 2. Verify season exists and belongs to the club
        val season = seasonRepository.getSeasonById(seasonId)
            ?: throw ValidationException("Season not found")

        if (season.clubId != clubId) {
            throw ForbiddenException("Season does not belong to this club")
        }

        return seasonRepository.updateActiveStatus(seasonId, active)
    }
}
