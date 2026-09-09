package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.SeasonRepository
import com.ricardofrango.clubmanager.model.Season

class GetSeasonsUseCase(
    private val seasonRepository: SeasonRepository
) {
    suspend operator fun invoke(clubId: String): List<Season> {
        return seasonRepository.getSeasons(clubId)
    }
}
