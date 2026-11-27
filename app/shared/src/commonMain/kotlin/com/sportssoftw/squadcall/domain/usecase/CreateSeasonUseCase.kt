package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.SeasonRepository
import com.sportssoftw.squadcall.model.CreateSeasonRequest
import com.sportssoftw.squadcall.model.Season

class CreateSeasonUseCase(
    private val seasonRepository: SeasonRepository
) {
    suspend operator fun invoke(request: CreateSeasonRequest): Season {
        return seasonRepository.createSeason(request)
    }
}
