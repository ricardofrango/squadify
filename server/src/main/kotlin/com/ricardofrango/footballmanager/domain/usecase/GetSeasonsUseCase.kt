package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.SeasonRepository
import com.ricardofrango.footballmanager.model.ClubRole
import com.ricardofrango.footballmanager.model.Season

class GetSeasonsUseCase(
    private val seasonRepository: SeasonRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase
) {
    suspend operator fun invoke(userId: String, clubId: String): List<Season> {
        // 1. Check permissions first (Member of club)
        checkUserPermissionUseCase(userId, clubId, minClubRole = ClubRole.MEMBER)

        return seasonRepository.getSeasonsByClub(clubId)
    }
}
