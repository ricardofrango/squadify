package com.ricardofrango.clubmanager.data.remote

import com.ricardofrango.clubmanager.domain.config.AppConfig
import com.ricardofrango.clubmanager.domain.repository.OpponentRepository
import com.ricardofrango.clubmanager.domain.repository.SessionRepository
import com.ricardofrango.clubmanager.model.CreateOpponentRequest
import com.ricardofrango.clubmanager.model.Opponent
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

class OpponentRepositoryImpl(
    private val httpClient: HttpClient,
    private val appConfig: AppConfig,
    private val sessionRepository: SessionRepository
) : OpponentRepository {

    override suspend fun getOpponents(clubId: String): List<Opponent> {
        val token = sessionRepository.getToken()
        return httpClient.get("${appConfig.baseUrl}/opponents") {
            parameter("clubId", clubId)
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }.handleResponse()
    }

    override suspend fun createOpponent(clubId: String, request: CreateOpponentRequest): Opponent {
        val token = sessionRepository.getToken()
        return httpClient.post("${appConfig.baseUrl}/opponents") {
            parameter("clubId", clubId)
            contentType(ContentType.Application.Json)
            setBody(request)
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }.handleResponse()
    }
}
