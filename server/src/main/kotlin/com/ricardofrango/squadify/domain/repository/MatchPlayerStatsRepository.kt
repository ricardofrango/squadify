package com.ricardofrango.squadify.domain.repository

import com.ricardofrango.squadify.model.MatchPlayerStats

interface MatchPlayerStatsRepository {
    // Replaces every row for this match with [stats] -- written once, at full time.
    suspend fun replaceForMatch(matchId: String, stats: List<MatchPlayerStats>)
    suspend fun getByMatch(matchId: String): List<MatchPlayerStats>
    // Sets (or clears, with null) the coach's 0-10 rating for each player in [ratings], keyed by
    // playerId. Players without a stats row for this match are ignored.
    suspend fun updateRatings(matchId: String, ratings: Map<String, Int?>)
}
