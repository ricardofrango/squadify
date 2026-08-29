package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.SeasonRepository
import com.ricardofrango.footballmanager.model.Role
import com.ricardofrango.footballmanager.model.Season

class GetSeasonsUseCase(
    private val seasonRepository: SeasonRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase
) {
    suspend operator fun invoke(userId: String, clubId: String): List<Season> {
        // 1. Check permissions first (Fail fast)
        checkUserPermissionUseCase(userId, clubId, Role.PLAYER)

        return seasonRepository.getSeasonsByClub(clubId)
    }
}
