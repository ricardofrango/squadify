package com.ricardofrango.squadify.data.remote

import com.ricardofrango.squadify.domain.config.AppConfig
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.SessionRepository
import com.ricardofrango.squadify.model.ClubMatchesUpdate
import com.ricardofrango.squadify.model.CreateMatchEventRequest
import com.ricardofrango.squadify.model.CreateMatchRequest
import com.ricardofrango.squadify.model.LiveMatchUpdate
import com.ricardofrango.squadify.model.ManageCallUpRequest
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchDetails
import com.ricardofrango.squadify.model.MatchEvent
import com.ricardofrango.squadify.model.MatchReport
import com.ricardofrango.squadify.model.MatchReportDetails
import com.ricardofrango.squadify.model.SaveMatchReportRequest
import com.ricardofrango.squadify.model.MatchScore
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.SetMatchMinuteRequest
import com.ricardofrango.squadify.model.StartMatchRequest
import com.ricardofrango.squadify.model.UpdateMatchRequest
import com.ricardofrango.squadify.model.UpdateMatchStatusRequest
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive

class MatchRepositoryImpl(
    private val httpClient: HttpClient,
    private val appConfig: AppConfig,
    private val sessionRepository: SessionRepository
) : MatchRepository {

    override suspend fun createMatch(request: CreateMatchRequest): Match =
        httpClient.post("${appConfig.baseUrl}/matches") {
            contentType(ContentType.Application.Json); setBody(request)
        }.handleResponse()

    override suspend fun getMatches(clubId: String): List<Match> =
        httpClient.get("${appConfig.baseUrl}/matches") {
            parameter("clubId", clubId)
        }.handleResponse()

    override suspend fun getMatchDetails(matchId: String): MatchDetails =
        httpClient.get("${appConfig.baseUrl}/matches/$matchId").handleResponse()

    override suspend fun updateMatch(matchId: String, request: UpdateMatchRequest) {
        httpClient.patch("${appConfig.baseUrl}/matches/$matchId") {
            contentType(ContentType.Application.Json); setBody(request)
        }.handleResponse<Unit>()
    }

    override suspend fun deleteMatch(matchId: String) {
        httpClient.delete("${appConfig.baseUrl}/matches/$matchId").handleResponse<Unit>()
    }

    override suspend fun updateMatchStatus(matchId: String, status: MatchStatus) {
        httpClient.patch("${appConfig.baseUrl}/matches/$matchId/status") {
            contentType(ContentType.Application.Json); setBody(UpdateMatchStatusRequest(status))
        }.handleResponse<Unit>()
    }

    override suspend fun getMatchReport(matchId: String): MatchReportDetails =
        httpClient.get("${appConfig.baseUrl}/matches/$matchId/report").handleResponse()

    override suspend fun saveMatchReport(matchId: String, request: SaveMatchReportRequest): MatchReport =
        httpClient.put("${appConfig.baseUrl}/matches/$matchId/report") {
            contentType(ContentType.Application.Json); setBody(request)
        }.handleResponse()

    override suspend fun manageCallUps(matchId: String, playerIds: List<String>) {
        httpClient.post("${appConfig.baseUrl}/matches/$matchId/call-up") {
            contentType(ContentType.Application.Json); setBody(ManageCallUpRequest(playerIds))
        }.handleResponse<Unit>()
    }

    override suspend fun startMatch(matchId: String, request: StartMatchRequest) {
        httpClient.post("${appConfig.baseUrl}/matches/$matchId/start") {
            contentType(ContentType.Application.Json); setBody(request)
        }.handleResponse<Unit>()
    }

    override suspend fun pauseMatch(matchId: String) {
        httpClient.post("${appConfig.baseUrl}/matches/$matchId/pause").handleResponse<Unit>()
    }

    override suspend fun resumeMatch(matchId: String) {
        httpClient.post("${appConfig.baseUrl}/matches/$matchId/resume").handleResponse<Unit>()
    }

    override suspend fun setMatchMinute(matchId: String, minute: Int) {
        httpClient.patch("${appConfig.baseUrl}/matches/$matchId/minute") {
            contentType(ContentType.Application.Json); setBody(SetMatchMinuteRequest(minute))
        }.handleResponse<Unit>()
    }

    override suspend fun getMatchEvents(matchId: String): List<MatchEvent> =
        httpClient.get("${appConfig.baseUrl}/matches/$matchId/events").handleResponse()

    override suspend fun getMatchScore(matchId: String): MatchScore =
        httpClient.get("${appConfig.baseUrl}/matches/$matchId/events/score").handleResponse()

    override suspend fun addMatchEvent(matchId: String, request: CreateMatchEventRequest): MatchEvent =
        httpClient.post("${appConfig.baseUrl}/matches/$matchId/events") {
            contentType(ContentType.Application.Json); setBody(request)
        }.handleResponse()

    override suspend fun deleteMatchEvent(matchId: String, eventId: String) {
        httpClient.delete("${appConfig.baseUrl}/matches/$matchId/events/$eventId").handleResponse<Unit>()
    }

    override fun observeLiveMatch(matchId: String): Flow<LiveMatchUpdate> = channelFlow {
        // Browser WebSockets can't send an Authorization header, so the token travels as a query
        // param instead -- mirrors the server's `/matches/{matchId}/live` route.
        val token = sessionRepository.getToken()
        val wsUrl = appConfig.baseUrl.replaceFirst("http", "ws")
        httpClient.webSocket("$wsUrl/matches/$matchId/live?token=$token") {
            while (isActive) {
                val update = receiveDeserialized<LiveMatchUpdate>()
                this@channelFlow.send(update)
            }
        }
    }

    override fun observeClubMatches(clubId: String): Flow<ClubMatchesUpdate> = channelFlow {
        val token = sessionRepository.getToken()
        val wsUrl = appConfig.baseUrl.replaceFirst("http", "ws")
        httpClient.webSocket("$wsUrl/clubs/$clubId/matches/live?token=$token") {
            while (isActive) {
                val update = receiveDeserialized<ClubMatchesUpdate>()
                this@channelFlow.send(update)
            }
        }
    }
}
