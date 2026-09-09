package com.ricardofrango.footballmanager.domain.repository

import com.ricardofrango.footballmanager.model.CreateMatchRequest
import com.ricardofrango.footballmanager.model.Match
import com.ricardofrango.footballmanager.model.MatchDetails
import com.ricardofrango.footballmanager.model.MatchStatus
import com.ricardofrango.footballmanager.model.UpdateCallUpRequest
import com.ricardofrango.footballmanager.model.UpdateMatchRequest

interface MatchRepository {
    suspend fun createMatch(request: CreateMatchRequest): Match
    suspend fun getMatches(clubId: String): List<Match>
    suspend fun getMatchDetails(matchId: String): MatchDetails
    suspend fun updateMatch(matchId: String, request: UpdateMatchRequest)
    suspend fun updateMatchStatus(matchId: String, status: MatchStatus)
    suspend fun manageCallUps(matchId: String, playerIds: List<String>)
    suspend fun updateCallUpStatus(callUpId: String, request: UpdateCallUpRequest)
}
