package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.SeasonRepository
import com.ricardofrango.footballmanager.model.Season

class GetSeasonsUseCase(
    private val seasonRepository: SeasonRepository
) {
    suspend operator fun invoke(clubId: String): List<Season> {
        return seasonRepository.getSeasons(clubId)
    }
}
