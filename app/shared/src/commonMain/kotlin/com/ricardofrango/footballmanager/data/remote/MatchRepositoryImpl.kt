package com.ricardofrango.footballmanager.data.remote

import com.ricardofrango.footballmanager.domain.config.AppConfig
import com.ricardofrango.footballmanager.domain.repository.MatchRepository
import com.ricardofrango.footballmanager.domain.repository.SessionRepository
import com.ricardofrango.footballmanager.model.CreateMatchRequest
import com.ricardofrango.footballmanager.model.ManageCallUpRequest
import com.ricardofrango.footballmanager.model.Match
import com.ricardofrango.footballmanager.model.MatchDetails
import com.ricardofrango.footballmanager.model.MatchStatus
import com.ricardofrango.footballmanager.model.UpdateCallUpRequest
import com.ricardofrango.footballmanager.model.UpdateMatchRequest
import com.ricardofrango.footballmanager.model.UpdateMatchStatusRequest
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

class MatchRepositoryImpl(
    private val httpClient: HttpClient,
    private val appConfig: AppConfig,
    private val sessionRepository: SessionRepository
) : MatchRepository {

    private suspend fun HttpRequestBuilder.auth() {
        sessionRepository.getToken()?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }

    override suspend fun createMatch(request: CreateMatchRequest): Match =
        httpClient.post("${appConfig.baseUrl}/matches") {
            contentType(ContentType.Application.Json); setBody(request); auth()
        }.handleResponse()

    override suspend fun getMatches(clubId: String): List<Match> =
        httpClient.get("${appConfig.baseUrl}/matches") {
            parameter("clubId", clubId); auth()
        }.handleResponse()

    override suspend fun getMatchDetails(matchId: String): MatchDetails =
        httpClient.get("${appConfig.baseUrl}/matches/$matchId") { auth() }.handleResponse()

    override suspend fun updateMatch(matchId: String, request: UpdateMatchRequest) {
        httpClient.patch("${appConfig.baseUrl}/matches/$matchId") {
            contentType(ContentType.Application.Json); setBody(request); auth()
        }.handleResponse<Unit>()
    }

    override suspend fun updateMatchStatus(matchId: String, status: MatchStatus) {
        httpClient.patch("${appConfig.baseUrl}/matches/$matchId/status") {
            contentType(ContentType.Application.Json); setBody(UpdateMatchStatusRequest(status)); auth()
        }.handleResponse<Unit>()
    }

    override suspend fun manageCallUps(matchId: String, playerIds: List<String>) {
        httpClient.post("${appConfig.baseUrl}/matches/$matchId/call-up") {
            contentType(ContentType.Application.Json); setBody(ManageCallUpRequest(playerIds)); auth()
        }.handleResponse<Unit>()
    }

    override suspend fun updateCallUpStatus(callUpId: String, request: UpdateCallUpRequest) {
        httpClient.patch("${appConfig.baseUrl}/matches/call-ups/$callUpId/status") {
            contentType(ContentType.Application.Json); setBody(request); auth()
        }.handleResponse<Unit>()
    }
}
