package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.MatchRepository
import com.ricardofrango.footballmanager.model.CreateMatchRequest
import com.ricardofrango.footballmanager.model.Match

class CreateMatchUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(request: CreateMatchRequest): Match {
        return matchRepository.createMatch(request)
    }
}
