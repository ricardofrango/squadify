package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.MatchRepository
import com.sportssoftw.squadcall.model.CreateMatchEventRequest

class AddMatchEventUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(matchId: String, request: CreateMatchEventRequest) =
        matchRepository.addMatchEvent(matchId, request)
}
