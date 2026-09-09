package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.SeasonRepository
import com.ricardofrango.clubmanager.model.CreateSeasonRequest
import com.ricardofrango.clubmanager.model.Season

class CreateSeasonUseCase(
    private val seasonRepository: SeasonRepository
) {
    suspend operator fun invoke(request: CreateSeasonRequest): Season {
        return seasonRepository.createSeason(request)
    }
}
