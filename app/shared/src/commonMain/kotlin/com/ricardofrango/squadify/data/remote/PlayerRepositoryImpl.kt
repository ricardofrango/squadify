package com.ricardofrango.squadify.data.remote

import com.ricardofrango.squadify.domain.config.AppConfig
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.model.CreatePlayersRequest
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.PlayerProfile
import com.ricardofrango.squadify.model.PlayerSeasonStats
import com.ricardofrango.squadify.model.UpdatePlayerRequest
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

class PlayerRepositoryImpl(
    private val httpClient: HttpClient,
    private val appConfig: AppConfig
) : PlayerRepository {

    override suspend fun getPlayers(clubId: String): List<Player> =
        httpClient.get("${appConfig.baseUrl}/players") {
            parameter("clubId", clubId)
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
}
