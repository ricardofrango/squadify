package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.MatchRepository
import com.ricardofrango.clubmanager.model.CreateMatchEventRequest

class AddMatchEventUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(matchId: String, request: CreateMatchEventRequest) =
        matchRepository.addMatchEvent(matchId, request)
}
