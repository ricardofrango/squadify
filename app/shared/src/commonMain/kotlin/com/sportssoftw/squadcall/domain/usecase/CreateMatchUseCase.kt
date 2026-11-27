package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.MatchRepository
import com.sportssoftw.squadcall.model.CreateMatchRequest
import com.sportssoftw.squadcall.model.Match

class CreateMatchUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(request: CreateMatchRequest): Match {
        return matchRepository.createMatch(request)
    }
}
