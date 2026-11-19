package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.MatchRepository
import com.sportssoftw.squadify.model.CreateMatchEventRequest

class AddMatchEventUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(matchId: String, request: CreateMatchEventRequest) =
        matchRepository.addMatchEvent(matchId, request)
}
