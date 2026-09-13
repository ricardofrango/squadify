package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.SeasonRepository
import com.ricardofrango.squadify.model.Season

class GetSeasonsUseCase(
    private val seasonRepository: SeasonRepository
) {
    suspend operator fun invoke(clubId: String): List<Season> {
        return seasonRepository.getSeasons(clubId)
    }
}
