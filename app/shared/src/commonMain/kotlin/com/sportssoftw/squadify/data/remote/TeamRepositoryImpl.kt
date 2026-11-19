package com.sportssoftw.squadify.data.remote

import com.sportssoftw.squadify.data.cache.cachedGet
import com.sportssoftw.squadify.data.cache.CacheKind
import com.sportssoftw.squadify.domain.config.AppConfig
import com.sportssoftw.squadify.domain.repository.TeamRepository
import com.sportssoftw.squadify.model.CreateTeamsRequest
import com.sportssoftw.squadify.model.Team
import com.sportssoftw.squadify.model.TeamDetails
import com.sportssoftw.squadify.model.TeamSeasonRecord
import com.sportssoftw.squadify.model.UpdateTeamRequest
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

class TeamRepositoryImpl(
    private val httpClient: HttpClient,
    private val appConfig: AppConfig
) : TeamRepository {

    override suspend fun getTeams(clubId: String): List<Team> =
        httpClient.cachedGet("${appConfig.baseUrl}/teams") {
            parameter("clubId", clubId)
        }

    override suspend fun getTeamDetails(teamId: String): TeamDetails =
        httpClient.cachedGet("${appConfig.baseUrl}/teams/$teamId/details", CacheKind.MATCH)

    override suspend fun getTeamSeasonRecord(teamId: String): TeamSeasonRecord =
        httpClient.cachedGet("${appConfig.baseUrl}/teams/$teamId/season-record", CacheKind.MATCH)

    override suspend fun getTeamFullStats(teamId: String): com.sportssoftw.squadify.model.FullStatsData =
        httpClient.cachedGet("${appConfig.baseUrl}/teams/$teamId/full-stats", CacheKind.MATCH)

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

    override suspend fun deleteTeam(teamId: String) {
        httpClient.delete("${appConfig.baseUrl}/teams/$teamId").handleResponse<Unit>()
    }
}
