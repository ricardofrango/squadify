package com.ricardofrango.footballmanager.data.remote

import com.ricardofrango.footballmanager.domain.config.AppConfig
import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.domain.repository.SessionRepository
import com.ricardofrango.footballmanager.model.Player
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
}
