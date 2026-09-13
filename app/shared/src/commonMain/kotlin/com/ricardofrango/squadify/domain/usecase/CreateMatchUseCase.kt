package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.model.CreateMatchRequest
import com.ricardofrango.squadify.model.Match

class CreateMatchUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(request: CreateMatchRequest): Match {
        return matchRepository.createMatch(request)
    }
}
