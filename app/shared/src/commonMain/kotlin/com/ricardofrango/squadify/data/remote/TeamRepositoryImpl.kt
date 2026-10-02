package com.ricardofrango.squadify.data.remote

import com.ricardofrango.squadify.domain.config.AppConfig
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.model.CreateTeamsRequest
import com.ricardofrango.squadify.model.Team
import com.ricardofrango.squadify.model.TeamDetails
import com.ricardofrango.squadify.model.TeamSeasonRecord
import com.ricardofrango.squadify.model.UpdateTeamRequest
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

class TeamRepositoryImpl(
    private val httpClient: HttpClient,
    private val appConfig: AppConfig
) : TeamRepository {

    override suspend fun getTeams(clubId: String): List<Team> =
        httpClient.get("${appConfig.baseUrl}/teams") {
            parameter("clubId", clubId)
        }.handleResponse()

    override suspend fun getTeamDetails(teamId: String): TeamDetails =
        httpClient.get("${appConfig.baseUrl}/teams/$teamId/details").handleResponse()

    override suspend fun getTeamSeasonRecord(teamId: String): TeamSeasonRecord =
        httpClient.get("${appConfig.baseUrl}/teams/$teamId/season-record").handleResponse()

    override suspend fun createTeams(request: CreateTeamsRequest): List<Team> =
        httpClient.post("${appConfig.baseUrl}/teams/batch") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.handleResponse()

    override suspend fun updateTeam(teamId: String, request: UpdateTeamRequest) {
        httpClient.patch("${appConfig.baseUrl}/teams/$teamId") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.handleResponse<Unit>()
    }
}
