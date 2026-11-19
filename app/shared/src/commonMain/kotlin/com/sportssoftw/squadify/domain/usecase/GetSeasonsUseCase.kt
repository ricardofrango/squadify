package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.SeasonRepository
import com.sportssoftw.squadify.model.Season

class GetSeasonsUseCase(
    private val seasonRepository: SeasonRepository
) {
    suspend operator fun invoke(clubId: String): List<Season> {
        return seasonRepository.getSeasons(clubId)
    }
}
