package com.sportssoftw.squadcall.data.remote

import com.sportssoftw.squadcall.data.cache.cachedGet
import com.sportssoftw.squadcall.data.cache.CacheKind
import com.sportssoftw.squadcall.domain.config.AppConfig
import com.sportssoftw.squadcall.domain.repository.TeamRepository
import com.sportssoftw.squadcall.model.CreateTeamsRequest
import com.sportssoftw.squadcall.model.Team
import com.sportssoftw.squadcall.model.TeamDetails
import com.sportssoftw.squadcall.model.TeamSeasonRecord
import com.sportssoftw.squadcall.model.UpdateTeamRequest
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

    override suspend fun getTeamFullStats(teamId: String): com.sportssoftw.squadcall.model.FullStatsData =
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
