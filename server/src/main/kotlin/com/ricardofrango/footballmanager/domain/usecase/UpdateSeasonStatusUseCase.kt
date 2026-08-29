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
        seasonId: String,
        active: Boolean
    ): Boolean {
        // 1. Verify season exists first to get context
        val season = seasonRepository.getSeasonById(seasonId)
            ?: throw ValidationException("Season not found")

        // 2. Check permissions using season's clubId
        checkUserPermissionUseCase(
            userId = userId,
            clubId = season.clubId,
            minRole = Role.COORDINATOR,
            errorMessage = "Only admins or coordinators can change season status"
        )

        return seasonRepository.updateActiveStatus(seasonId, active)
    }
}
