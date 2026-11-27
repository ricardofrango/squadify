package com.sportssoftw.squadcall.data.remote

import com.sportssoftw.squadcall.data.cache.cachedGet
import com.sportssoftw.squadcall.data.cache.CacheKind
import com.sportssoftw.squadcall.domain.service.SocketEvent
import com.sportssoftw.squadcall.domain.config.AppConfig
import com.sportssoftw.squadcall.domain.repository.MatchRepository
import com.sportssoftw.squadcall.domain.repository.SessionRepository
import com.sportssoftw.squadcall.model.ClubMatchesUpdate
import com.sportssoftw.squadcall.model.CreateMatchEventRequest
import com.sportssoftw.squadcall.model.CreateMatchRequest
import com.sportssoftw.squadcall.model.LiveMatchUpdate
import com.sportssoftw.squadcall.model.ManageCallUpRequest
import com.sportssoftw.squadcall.model.Match
import com.sportssoftw.squadcall.model.MatchDetails
import com.sportssoftw.squadcall.model.MatchListStatus
import com.sportssoftw.squadcall.model.MatchesPage
import com.sportssoftw.squadcall.model.MatchEvent
import com.sportssoftw.squadcall.model.MatchReport
import com.sportssoftw.squadcall.model.MatchReportDetails
import com.sportssoftw.squadcall.model.SaveMatchReportRequest
import com.sportssoftw.squadcall.model.SaveMatchRecordRequest
import com.sportssoftw.squadcall.model.MatchScore
import com.sportssoftw.squadcall.model.MatchStatus
import com.sportssoftw.squadcall.model.PLAN_LIMIT_CODE
import com.sportssoftw.squadcall.model.SetMatchMinuteRequest
import com.sportssoftw.squadcall.model.StartMatchRequest
import com.sportssoftw.squadcall.model.SetMatchLineupRequest
import com.sportssoftw.squadcall.model.MatchLineup
import com.sportssoftw.squadcall.model.UpdateMatchRequest
import com.sportssoftw.squadcall.model.UpdateMatchStatusRequest
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
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

    override suspend fun getMatchesPage(
        clubId: String,
        status: MatchListStatus,
        seasonId: String?,
        ageGroupId: String?,
        teamId: String?,
        offset: Int,
        limit: Int
    ): MatchesPage =
        httpClient.cachedGet("${appConfig.baseUrl}/matches", CacheKind.MATCH) {
            parameter("clubId", clubId)
            parameter("status", status.name)
            seasonId?.let { parameter("seasonId", it) }
            ageGroupId?.let { parameter("ageGroupId", it) }
            teamId?.let { parameter("teamId", it) }
            parameter("limit", limit)
            parameter("offset", offset)
        }

    override suspend fun getMatchDetails(matchId: String): MatchDetails =
        httpClient.get("${appConfig.baseUrl}/matches/$matchId").handleResponse()

    override suspend fun getMatchDetailsKept(matchId: String): MatchDetails =
        httpClient.cachedGet("${appConfig.baseUrl}/matches/$matchId", CacheKind.MATCH)

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

    override suspend fun saveMatchRecord(matchId: String, request: SaveMatchRecordRequest): Match =
        httpClient.put("${appConfig.baseUrl}/matches/$matchId/record") {
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

    override suspend fun setLineup(matchId: String, request: SetMatchLineupRequest): MatchLineup =
        httpClient.put("${appConfig.baseUrl}/matches/$matchId/lineup") {
            contentType(ContentType.Application.Json); setBody(request)
        }.handleResponse()

    override suspend fun takeOverLiveMatch(matchId: String) {
        httpClient.post("${appConfig.baseUrl}/matches/$matchId/take-over").handleResponse<Unit>()
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

    override fun observeLiveMatch(matchId: String): Flow<SocketEvent<LiveMatchUpdate>> = channelFlow {
        // Browser WebSockets can't send an Authorization header, so the token travels as a query
        // param instead -- mirrors the server's `/matches/{matchId}/live` route. Read on every
        // (re)connect, so a reconnect uses the token the last REST call refreshed.
        val token = sessionRepository.getToken()
        val wsUrl = appConfig.baseUrl.replaceFirst("http", "ws")
        httpClient.webSocket("$wsUrl/matches/$matchId/live?token=$token") {
            this@channelFlow.send(SocketEvent.Open)
            try {
                while (isActive) {
                    val update = receiveDeserialized<LiveMatchUpdate>()
                    this@channelFlow.send(SocketEvent.Message(update))
                }
            } catch (e: ClosedReceiveChannelException) {
                if (closeReason.await()?.message == PLAN_LIMIT_CODE) this@channelFlow.send(SocketEvent.NotInPlan) else throw e
            }
        }
    }

    override fun observeClubMatches(clubId: String): Flow<SocketEvent<ClubMatchesUpdate>> = channelFlow {
        val token = sessionRepository.getToken()
        val wsUrl = appConfig.baseUrl.replaceFirst("http", "ws")
        httpClient.webSocket("$wsUrl/clubs/$clubId/matches/live?token=$token") {
            this@channelFlow.send(SocketEvent.Open)
            try {
                while (isActive) {
                    val update = receiveDeserialized<ClubMatchesUpdate>()
                    this@channelFlow.send(SocketEvent.Message(update))
                }
            } catch (e: ClosedReceiveChannelException) {
                if (closeReason.await()?.message == PLAN_LIMIT_CODE) this@channelFlow.send(SocketEvent.NotInPlan) else throw e
            }
        }
    }
}
