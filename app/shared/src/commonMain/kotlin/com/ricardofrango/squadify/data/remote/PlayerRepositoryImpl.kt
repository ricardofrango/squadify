package com.ricardofrango.squadify.data.remote

import com.ricardofrango.squadify.domain.config.AppConfig
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.model.CreatePlayersRequest
import com.ricardofrango.squadify.model.PhotoResponse
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.PlayersPage
import com.ricardofrango.squadify.model.PlayerProfile
import com.ricardofrango.squadify.model.PlayerSeasonStats
import com.ricardofrango.squadify.model.UpdatePlayerRequest
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.ByteArrayContent

class PlayerRepositoryImpl(
    private val httpClient: HttpClient,
    private val appConfig: AppConfig
) : PlayerRepository {

    override suspend fun getPlayersPage(
        clubId: String,
        seasonId: String?,
        ageGroupId: String?,
        query: String,
        offset: Int,
        limit: Int
    ): PlayersPage =
        httpClient.get("${appConfig.baseUrl}/players") {
            parameter("clubId", clubId)
            seasonId?.let { parameter("seasonId", it) }
            ageGroupId?.let { parameter("ageGroupId", it) }
            query.trim().takeIf { it.isNotEmpty() }?.let { parameter("q", it) }
            parameter("limit", limit)
            parameter("offset", offset)
        }.handleResponse()

    override suspend fun getPlayerById(playerId: String): PlayerProfile =
        httpClient.get("${appConfig.baseUrl}/players/$playerId").handleResponse()

    override suspend fun getPlayerSeasonStats(playerId: String): PlayerSeasonStats =
        httpClient.get("${appConfig.baseUrl}/players/$playerId/season-stats").handleResponse()

    override suspend fun createPlayers(request: CreatePlayersRequest): List<Player> =
        httpClient.post("${appConfig.baseUrl}/players") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.handleResponse()

    override suspend fun updatePlayer(playerId: String, request: UpdatePlayerRequest) {
        httpClient.patch("${appConfig.baseUrl}/players/$playerId") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.handleResponse<Unit>()
    }

    override suspend fun disablePlayer(playerId: String) {
        httpClient.post("${appConfig.baseUrl}/players/$playerId/disable").handleResponse<Unit>()
    }

    override suspend fun reactivatePlayer(playerId: String) {
        httpClient.post("${appConfig.baseUrl}/players/$playerId/reactivate").handleResponse<Unit>()
    }

    override suspend fun uploadPlayerPhoto(playerId: String, bytes: ByteArray): String =
        httpClient.put("${appConfig.baseUrl}/players/$playerId/photo") {
            setBody(ByteArrayContent(bytes, ContentType.Application.OctetStream))
        }.handleResponse<PhotoResponse>().photoUrl

    override suspend fun deletePlayerPhoto(playerId: String) {
        httpClient.delete("${appConfig.baseUrl}/players/$playerId/photo").handleResponse<Unit>()
    }
}
