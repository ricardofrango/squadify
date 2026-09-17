package com.ricardofrango.squadify.domain.repository

import com.ricardofrango.squadify.model.*

interface MatchRepository {
    suspend fun getMatchById(id: String): Match?
    suspend fun getMatchesByTeam(teamId: String): List<Match>
    suspend fun getMatchesByClub(clubId: String): List<Match>
    suspend fun createMatch(match: Match): Match
    suspend fun updateMatch(match: Match): Boolean
    suspend fun updateMatchStatus(matchId: String, status: MatchStatus): Boolean
    // Kicks off: sets isStarting=true on [startingPlayerIds]' call-ups (false on every other call-up
    // of this match), and sets the match's status to LIVE and its half length -- all in one write.
    suspend fun startMatch(matchId: String, startingPlayerIds: List<String>, halfLengthMinutes: Int): Boolean
    // Records the wall-clock instant the second half resumed -- see UpdateMatchStatusUseCase, which
    // calls this on a HALF_TIME -> LIVE transition so the client can derive the running clock from it.
    suspend fun markSecondHalfStarted(matchId: String, at: kotlin.time.Instant): Boolean

    suspend fun getCallUpsForMatch(matchId: String): List<MatchCallUpWithPlayer>
    suspend fun countCallUpsByPlayer(playerId: String): Long
    suspend fun manageCallUps(matchId: String, playerIds: List<String>): Boolean
    suspend fun getCallUpByMatchAndPlayer(matchId: String, playerId: String): MatchCallUp?
    suspend fun getUpcomingMatchesByClub(clubId: String, limit: Int): List<Match>
    suspend fun getUpcomingMatchesByPlayer(playerId: String, limit: Int): List<Match>
}
