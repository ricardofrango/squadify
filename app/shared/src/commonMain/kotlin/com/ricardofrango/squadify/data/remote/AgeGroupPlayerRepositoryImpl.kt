package com.ricardofrango.squadify.data.remote

import com.ricardofrango.squadify.domain.config.AppConfig
import com.ricardofrango.squadify.domain.repository.AgeGroupPlayerRepository
import com.ricardofrango.squadify.model.AddPlayersToAgeGroupRequest
import com.ricardofrango.squadify.model.AgeGroupPlayer
import com.ricardofrango.squadify.model.PlayersPage
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

class AgeGroupPlayerRepositoryImpl(
    private val httpClient: HttpClient,
    private val appConfig: AppConfig
) : AgeGroupPlayerRepository {

    override suspend fun getEligiblePlayersPage(ageGroupId: String, query: String, offset: Int, limit: Int): PlayersPage =
        httpClient.get("${appConfig.baseUrl}/age-groups/$ageGroupId/players/eligible") {
            query.trim().takeIf { it.isNotEmpty() }?.let { parameter("q", it) }
            parameter("limit", limit)
            parameter("offset", offset)
        }.handleResponse()

    override suspend fun addPlayers(ageGroupId: String, playerIds: List<String>): List<AgeGroupPlayer> =
        httpClient.post("${appConfig.baseUrl}/age-groups/$ageGroupId/players") {
            contentType(ContentType.Application.Json)
            setBody(AddPlayersToAgeGroupRequest(playerIds))
        }.handleResponse()

    override suspend fun removePlayer(ageGroupId: String, playerId: String) {
        httpClient.delete("${appConfig.baseUrl}/age-groups/$ageGroupId/players/$playerId").handleResponse<Unit>()
    }
}
