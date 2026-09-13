package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.model.CreateMatchEventRequest

class AddMatchEventUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(matchId: String, request: CreateMatchEventRequest) =
        matchRepository.addMatchEvent(matchId, request)
}
