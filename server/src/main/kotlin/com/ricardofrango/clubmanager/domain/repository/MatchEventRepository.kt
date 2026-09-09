package com.ricardofrango.clubmanager.domain.repository

import com.ricardofrango.clubmanager.model.MatchEvent
import com.ricardofrango.clubmanager.model.MatchScore

interface MatchEventRepository {
    suspend fun addEvent(event: MatchEvent): MatchEvent
    suspend fun getEventsByMatch(matchId: String): List<MatchEvent>
    suspend fun getScoreByMatch(matchId: String): MatchScore
    suspend fun getRecentEventsByPlayer(playerId: String, limit: Int): List<MatchEvent>
}
