package com.ricardofrango.clubmanager.domain.repository

import com.ricardofrango.clubmanager.model.CreateMatchRequest
import com.ricardofrango.clubmanager.model.Match
import com.ricardofrango.clubmanager.model.MatchDetails
import com.ricardofrango.clubmanager.model.MatchStatus
import com.ricardofrango.clubmanager.model.UpdateCallUpRequest
import com.ricardofrango.clubmanager.model.UpdateMatchRequest

interface MatchRepository {
    suspend fun createMatch(request: CreateMatchRequest): Match
    suspend fun getMatches(clubId: String): List<Match>
    suspend fun getMatchDetails(matchId: String): MatchDetails
    suspend fun updateMatch(matchId: String, request: UpdateMatchRequest)
    suspend fun updateMatchStatus(matchId: String, status: MatchStatus)
    suspend fun manageCallUps(matchId: String, playerIds: List<String>)
    suspend fun updateCallUpStatus(callUpId: String, request: UpdateCallUpRequest)
}
