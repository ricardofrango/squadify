package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.SeasonRepository
import com.sportssoftw.squadify.model.CreateSeasonRequest
import com.sportssoftw.squadify.model.Season

class CreateSeasonUseCase(
    private val seasonRepository: SeasonRepository
) {
    suspend operator fun invoke(request: CreateSeasonRequest): Season {
        return seasonRepository.createSeason(request)
    }
}
