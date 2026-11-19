package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.MatchRepository
import com.sportssoftw.squadify.model.CreateMatchRequest
import com.sportssoftw.squadify.model.Match

class CreateMatchUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(request: CreateMatchRequest): Match {
        return matchRepository.createMatch(request)
    }
}
