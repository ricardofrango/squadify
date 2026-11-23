package com.sportssoftw.squadify.data.remote

import com.sportssoftw.squadify.data.cache.cachedGet
import com.sportssoftw.squadify.data.cache.CacheKind
import com.sportssoftw.squadify.domain.config.AppConfig
import com.sportssoftw.squadify.domain.repository.PlayerRepository
import com.sportssoftw.squadify.model.CreatePlayersRequest
import com.sportssoftw.squadify.model.PhotoResponse
import com.sportssoftw.squadify.model.Player
import com.sportssoftw.squadify.model.PlayersPage
import com.sportssoftw.squadify.model.PlayerProfile
import com.sportssoftw.squadify.model.PlayerSeasonStats
import com.sportssoftw.squadify.model.UpdatePlayerRequest
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
        httpClient.cachedGet("${appConfig.baseUrl}/players") {
            parameter("clubId", clubId)
            seasonId?.let { parameter("seasonId", it) }
            ageGroupId?.let { parameter("ageGroupId", it) }
            query.trim().takeIf { it.isNotEmpty() }?.let { parameter("q", it) }
            parameter("limit", limit)
            parameter("offset", offset)
        }

    override suspend fun getPlayerById(playerId: String): PlayerProfile =
        httpClient.cachedGet("${appConfig.baseUrl}/players/$playerId")

    override suspend fun getPlayerSeasonStats(playerId: String): PlayerSeasonStats =
        httpClient.cachedGet("${appConfig.baseUrl}/players/$playerId/season-stats", CacheKind.MATCH)

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
