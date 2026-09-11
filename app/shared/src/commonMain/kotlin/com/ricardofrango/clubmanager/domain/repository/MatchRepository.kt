package com.ricardofrango.clubmanager.domain.repository

import com.ricardofrango.clubmanager.model.CreateMatchEventRequest
import com.ricardofrango.clubmanager.model.CreateMatchRequest
import com.ricardofrango.clubmanager.model.LiveMatchUpdate
import com.ricardofrango.clubmanager.model.Match
import com.ricardofrango.clubmanager.model.MatchDetails
import com.ricardofrango.clubmanager.model.MatchEvent
import com.ricardofrango.clubmanager.model.MatchScore
import com.ricardofrango.clubmanager.model.MatchStatus
import com.ricardofrango.clubmanager.model.StartMatchRequest
import com.ricardofrango.clubmanager.model.UpdateCallUpRequest
import com.ricardofrango.clubmanager.model.UpdateMatchRequest
import kotlinx.coroutines.flow.Flow

interface MatchRepository {
    suspend fun createMatch(request: CreateMatchRequest): Match
    suspend fun getMatches(clubId: String): List<Match>
    suspend fun getMatchDetails(matchId: String): MatchDetails
    suspend fun updateMatch(matchId: String, request: UpdateMatchRequest)
    suspend fun updateMatchStatus(matchId: String, status: MatchStatus)
    suspend fun startMatch(matchId: String, request: StartMatchRequest)
    suspend fun manageCallUps(matchId: String, playerIds: List<String>)
    suspend fun updateCallUpStatus(callUpId: String, request: UpdateCallUpRequest)

    suspend fun getMatchEvents(matchId: String): List<MatchEvent>
    suspend fun getMatchScore(matchId: String): MatchScore
    suspend fun addMatchEvent(matchId: String, request: CreateMatchEventRequest): MatchEvent
    suspend fun deleteMatchEvent(matchId: String, eventId: String)
    // Cold: opens a WebSocket to `/matches/{matchId}/live` when collected and closes it when the
    // collector cancels. Reconnection on drop is the caller's job (see LiveMatchViewModel).
    fun observeLiveMatch(matchId: String): Flow<LiveMatchUpdate>
}
