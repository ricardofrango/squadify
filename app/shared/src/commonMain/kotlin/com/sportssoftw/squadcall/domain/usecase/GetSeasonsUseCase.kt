package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.SeasonRepository
import com.sportssoftw.squadcall.model.Season

class GetSeasonsUseCase(
    private val seasonRepository: SeasonRepository
) {
    suspend operator fun invoke(clubId: String): List<Season> {
        return seasonRepository.getSeasons(clubId)
    }
}
