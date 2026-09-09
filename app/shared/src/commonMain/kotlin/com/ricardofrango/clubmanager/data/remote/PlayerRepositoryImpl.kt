package com.ricardofrango.clubmanager.data.remote

import com.ricardofrango.clubmanager.domain.config.AppConfig
import com.ricardofrango.clubmanager.domain.repository.PlayerRepository
import com.ricardofrango.clubmanager.domain.repository.SessionRepository
import com.ricardofrango.clubmanager.model.CreatePlayersRequest
import com.ricardofrango.clubmanager.model.Player
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

class PlayerRepositoryImpl(
    private val httpClient: HttpClient,
    private val appConfig: AppConfig,
    private val sessionRepository: SessionRepository
) : PlayerRepository {

    override suspend fun getPlayers(clubId: String): List<Player> {
        val token = sessionRepository.getToken()
        return httpClient.get("${appConfig.baseUrl}/players") {
            parameter("clubId", clubId)
            token?.let {
                header(HttpHeaders.Authorization, "Bearer $it")
            }
        }.handleResponse()
    }

    override suspend fun getPlayerById(playerId: String): Player {
        val token = sessionRepository.getToken()
        return httpClient.get("${appConfig.baseUrl}/players/$playerId") {
            token?.let {
                header(HttpHeaders.Authorization, "Bearer $it")
            }
        }.handleResponse()
    }

    override suspend fun createPlayers(request: CreatePlayersRequest): List<Player> {
        val token = sessionRepository.getToken()
        return httpClient.post("${appConfig.baseUrl}/players") {
            contentType(ContentType.Application.Json)
            setBody(request)
            token?.let {
                header(HttpHeaders.Authorization, "Bearer $it")
            }
        }.handleResponse()
    }
}
