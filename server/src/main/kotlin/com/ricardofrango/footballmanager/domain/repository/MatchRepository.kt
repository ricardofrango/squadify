package com.ricardofrango.footballmanager.domain.repository

import com.ricardofrango.footballmanager.model.*

interface MatchRepository {
    suspend fun getMatchById(id: String): Match?
    suspend fun getMatchesByTeam(teamId: String): List<Match>
    suspend fun getMatchesByClub(clubId: String): List<Match>
    suspend fun createMatch(match: Match): Match
    suspend fun updateMatch(match: Match): Boolean
    suspend fun updateMatchStatus(matchId: String, status: MatchStatus): Boolean
    
    suspend fun getCallUpsForMatch(matchId: String): List<MatchCallUpWithPlayer>
    suspend fun manageCallUps(matchId: String, playerIds: List<String>): Boolean
    suspend fun updateCallUp(callUpId: String, status: CallUpStatus, notes: String?): Boolean
    suspend fun getCallUpByMatchAndPlayer(matchId: String, playerId: String): MatchCallUp?
    suspend fun getCallUpById(id: String): MatchCallUp?
    suspend fun getUpcomingMatchesByClub(clubId: String, limit: Int): List<Match>
    suspend fun getUpcomingMatchesByPlayer(playerId: String, limit: Int): List<Match>
}
