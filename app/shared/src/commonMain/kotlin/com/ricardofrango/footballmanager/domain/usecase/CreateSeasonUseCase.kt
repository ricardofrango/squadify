package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.SeasonRepository
import com.ricardofrango.footballmanager.model.CreateSeasonRequest
import com.ricardofrango.footballmanager.model.Season

class CreateSeasonUseCase(
    private val seasonRepository: SeasonRepository
) {
    suspend operator fun invoke(request: CreateSeasonRequest): Season {
        return seasonRepository.createSeason(request)
    }
}
