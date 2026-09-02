package com.ricardofrango.footballmanager.data.remote

import com.ricardofrango.footballmanager.domain.config.AppConfig
import com.ricardofrango.footballmanager.domain.repository.SessionRepository
import com.ricardofrango.footballmanager.domain.repository.TeamPlayerRepository
import com.ricardofrango.footballmanager.model.TeamPlayer
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

class TeamPlayerRepositoryImpl(
    private val httpClient: HttpClient,
    private val appConfig: AppConfig,
    private val sessionRepository: SessionRepository
) : TeamPlayerRepository {

    override suspend fun getTeamPlayersByClub(clubId: String): List<TeamPlayer> {
        val token = sessionRepository.getToken()
        return httpClient.get("${appConfig.baseUrl}/players/associations/team") {
            parameter("clubId", clubId)
            token?.let {
                header(HttpHeaders.Authorization, "Bearer $it")
            }
        }.handleResponse()
    }
}
