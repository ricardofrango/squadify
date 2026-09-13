package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.SeasonRepository
import com.ricardofrango.squadify.model.CreateSeasonRequest
import com.ricardofrango.squadify.model.Season

class CreateSeasonUseCase(
    private val seasonRepository: SeasonRepository
) {
    suspend operator fun invoke(request: CreateSeasonRequest): Season {
        return seasonRepository.createSeason(request)
    }
}
