package com.ricardofrango.footballmanager.domain.repository

import com.ricardofrango.footballmanager.model.MatchEvent
import com.ricardofrango.footballmanager.model.MatchScore

interface MatchEventRepository {
    suspend fun addEvent(event: MatchEvent): MatchEvent
    suspend fun getEventsByMatch(matchId: String): List<MatchEvent>
    suspend fun getScoreByMatch(matchId: String): MatchScore
}
