package com.ricardofrango.footballmanager.data.remote

import com.ricardofrango.footballmanager.domain.config.AppConfig
import com.ricardofrango.footballmanager.domain.repository.AgeGroupPlayerRepository
import com.ricardofrango.footballmanager.domain.repository.SessionRepository
import com.ricardofrango.footballmanager.model.AddPlayersToAgeGroupRequest
import com.ricardofrango.footballmanager.model.AgeGroupPlayer
import com.ricardofrango.footballmanager.model.Player
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

class AgeGroupPlayerRepositoryImpl(
    private val httpClient: HttpClient,
    private val appConfig: AppConfig,
    private val sessionRepository: SessionRepository
) : AgeGroupPlayerRepository {

    override suspend fun getAgeGroupPlayersByClub(clubId: String): List<AgeGroupPlayer> {
        val token = sessionRepository.getToken()
        return httpClient.get("${appConfig.baseUrl}/players/associations/age-group") {
            parameter("clubId", clubId)
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }.handleResponse()
    }

    override suspend fun getEligiblePlayers(ageGroupId: String): List<Player> {
        val token = sessionRepository.getToken()
        return httpClient.get("${appConfig.baseUrl}/age-groups/$ageGroupId/players/eligible") {
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }.handleResponse()
    }

    override suspend fun addPlayers(ageGroupId: String, playerIds: List<String>): List<AgeGroupPlayer> {
        val token = sessionRepository.getToken()
        return httpClient.post("${appConfig.baseUrl}/age-groups/$ageGroupId/players") {
            contentType(ContentType.Application.Json)
            setBody(AddPlayersToAgeGroupRequest(playerIds))
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }.handleResponse()
    }
}
