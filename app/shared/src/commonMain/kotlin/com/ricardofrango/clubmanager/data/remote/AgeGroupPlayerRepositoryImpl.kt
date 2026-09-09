package com.ricardofrango.clubmanager.data.remote

import com.ricardofrango.clubmanager.domain.config.AppConfig
import com.ricardofrango.clubmanager.domain.repository.AgeGroupPlayerRepository
import com.ricardofrango.clubmanager.domain.repository.SessionRepository
import com.ricardofrango.clubmanager.model.AddPlayersToAgeGroupRequest
import com.ricardofrango.clubmanager.model.AgeGroupPlayer
import com.ricardofrango.clubmanager.model.Player
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
