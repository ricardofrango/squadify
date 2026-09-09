package com.ricardofrango.footballmanager.data.remote

import com.ricardofrango.footballmanager.domain.config.AppConfig
import com.ricardofrango.footballmanager.domain.repository.MatchRepository
import com.ricardofrango.footballmanager.domain.repository.SessionRepository
import com.ricardofrango.footballmanager.model.CreateMatchRequest
import com.ricardofrango.footballmanager.model.Match
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

class MatchRepositoryImpl(
    private val httpClient: HttpClient,
    private val appConfig: AppConfig,
    private val sessionRepository: SessionRepository
) : MatchRepository {

    override suspend fun createMatch(request: CreateMatchRequest): Match {
        val token = sessionRepository.getToken()
        return httpClient.post("${appConfig.baseUrl}/matches") {
            contentType(ContentType.Application.Json)
            setBody(request)
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }.handleResponse()
    }

    override suspend fun getMatches(clubId: String): List<Match> {
        val token = sessionRepository.getToken()
        return httpClient.get("${appConfig.baseUrl}/matches") {
            parameter("clubId", clubId)
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }.handleResponse()
    }
}
