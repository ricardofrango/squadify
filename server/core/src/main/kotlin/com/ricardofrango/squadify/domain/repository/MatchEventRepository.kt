package com.ricardofrango.squadify.domain.repository

import com.ricardofrango.squadify.model.MatchEvent
import com.ricardofrango.squadify.model.MatchScore
import com.ricardofrango.squadify.model.MatchTallies

interface MatchEventRepository {
    suspend fun addEvent(event: MatchEvent): MatchEvent
    suspend fun getEventById(id: String): MatchEvent?
    suspend fun deleteEvent(id: String): Boolean
    // Rewrites what an event says (type, minute, half, player); its id, match and createdAt stay.
    suspend fun updateEvent(event: MatchEvent): Boolean
    // Used by DeleteMatchUseCase to clear a match's events before deleting the match row itself.
    suspend fun deleteEventsByMatch(matchId: String)
    suspend fun getEventsByMatch(matchId: String): List<MatchEvent>
    suspend fun getScoreByMatch(matchId: String): MatchScore
    // The same, for many matches in one query; a match with no goals gets 0-0.
    suspend fun getScoresByMatches(matchIds: List<String>): Map<String, MatchScore>
    // Each match's shots, fouls and corners (MatchTallies.kt), in one query; none recorded: all 0.
    suspend fun getTalliesByMatches(matchIds: List<String>): Map<String, MatchTallies>
    suspend fun getRecentEventsByPlayer(playerId: String, limit: Int): List<MatchEvent>
    // Newest first, no limit -- used to compute a player's all-time goal/assist counts, with the
    // first few also doubling as the "recent events" list on their profile.
    suspend fun getAllEventsByPlayer(playerId: String): List<MatchEvent>
}
