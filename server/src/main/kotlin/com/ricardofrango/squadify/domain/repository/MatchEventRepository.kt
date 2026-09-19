package com.ricardofrango.squadify.domain.repository

import com.ricardofrango.squadify.model.MatchEvent
import com.ricardofrango.squadify.model.MatchScore

interface MatchEventRepository {
    suspend fun addEvent(event: MatchEvent): MatchEvent
    suspend fun getEventById(id: String): MatchEvent?
    suspend fun deleteEvent(id: String): Boolean
    // Used by DeleteMatchUseCase to clear a match's events before deleting the match row itself.
    suspend fun deleteEventsByMatch(matchId: String)
    suspend fun getEventsByMatch(matchId: String): List<MatchEvent>
    suspend fun getScoreByMatch(matchId: String): MatchScore
    suspend fun getRecentEventsByPlayer(playerId: String, limit: Int): List<MatchEvent>
    // Newest first, no limit -- used to compute a player's all-time goal/assist counts, with the
    // first few also doubling as the "recent events" list on their profile.
    suspend fun getAllEventsByPlayer(playerId: String): List<MatchEvent>
}
