package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.MatchEventRepository
import com.ricardofrango.footballmanager.model.MatchEvent
import com.ricardofrango.footballmanager.model.MatchScore

class GetMatchEventsUseCase(
    private val matchEventRepository: MatchEventRepository
) {
    suspend fun getEvents(matchId: String): List<MatchEvent> {
        return matchEventRepository.getEventsByMatch(matchId)
    }

    suspend fun getScore(matchId: String): MatchScore {
        return matchEventRepository.getScoreByMatch(matchId)
    }
}
