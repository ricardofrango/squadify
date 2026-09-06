package com.ricardofrango.footballmanager.data.remote

import com.ricardofrango.footballmanager.domain.config.AppConfig
import com.ricardofrango.footballmanager.domain.repository.SessionRepository
import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.model.CreateTeamsRequest
import com.ricardofrango.footballmanager.model.Team
import com.ricardofrango.footballmanager.model.TeamDetails
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

class TeamRepositoryImpl(
    private val httpClient: HttpClient,
    private val appConfig: AppConfig,
    private val sessionRepository: SessionRepository
) : TeamRepository {

    override suspend fun getTeams(clubId: String): List<Team> {
        val token = sessionRepository.getToken()
        return httpClient.get("${appConfig.baseUrl}/teams") {
            parameter("clubId", clubId)
            token?.let {
                header(HttpHeaders.Authorization, "Bearer $it")
            }
        }.handleResponse()
    }

    override suspend fun getTeamDetails(teamId: String): TeamDetails {
        val token = sessionRepository.getToken()
        return httpClient.get("${appConfig.baseUrl}/teams/$teamId/details") {
            token?.let {
                header(HttpHeaders.Authorization, "Bearer $it")
            }
        }.handleResponse()
    }

    override suspend fun createTeams(request: CreateTeamsRequest): List<Team> {
        val token = sessionRepository.getToken()
        return httpClient.post("${appConfig.baseUrl}/teams/batch") {
            contentType(ContentType.Application.Json)
            setBody(request)
            token?.let {
                header(HttpHeaders.Authorization, "Bearer $it")
            }
        }.handleResponse()
    }
}
