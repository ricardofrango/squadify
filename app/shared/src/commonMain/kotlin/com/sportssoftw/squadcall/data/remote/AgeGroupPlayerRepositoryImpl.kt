package com.sportssoftw.squadcall.data.remote

import com.sportssoftw.squadcall.data.cache.cachedGet
import com.sportssoftw.squadcall.domain.config.AppConfig
import com.sportssoftw.squadcall.domain.repository.AgeGroupPlayerRepository
import com.sportssoftw.squadcall.model.AddPlayersToAgeGroupRequest
import com.sportssoftw.squadcall.model.AgeGroupPlayer
import com.sportssoftw.squadcall.model.PlayersPage
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

class AgeGroupPlayerRepositoryImpl(
    private val httpClient: HttpClient,
    private val appConfig: AppConfig
) : AgeGroupPlayerRepository {

    override suspend fun getEligiblePlayersPage(ageGroupId: String, query: String, offset: Int, limit: Int): PlayersPage =
        httpClient.cachedGet("${appConfig.baseUrl}/age-groups/$ageGroupId/players/eligible") {
            query.trim().takeIf { it.isNotEmpty() }?.let { parameter("q", it) }
            parameter("limit", limit)
            parameter("offset", offset)
        }

    override suspend fun addPlayers(ageGroupId: String, playerIds: List<String>): List<AgeGroupPlayer> =
        httpClient.post("${appConfig.baseUrl}/age-groups/$ageGroupId/players") {
            contentType(ContentType.Application.Json)
            setBody(AddPlayersToAgeGroupRequest(playerIds))
        }.handleResponse()

    override suspend fun removePlayer(ageGroupId: String, playerId: String) {
        httpClient.delete("${appConfig.baseUrl}/age-groups/$ageGroupId/players/$playerId").handleResponse<Unit>()
    }
}
