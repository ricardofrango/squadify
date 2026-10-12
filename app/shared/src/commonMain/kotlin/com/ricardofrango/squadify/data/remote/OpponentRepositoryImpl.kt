package com.ricardofrango.squadify.data.remote

import com.ricardofrango.squadify.domain.config.AppConfig
import com.ricardofrango.squadify.domain.repository.OpponentRepository
import com.ricardofrango.squadify.model.CreateOpponentRequest
import com.ricardofrango.squadify.model.Opponent
import com.ricardofrango.squadify.model.OpponentDetails
import com.ricardofrango.squadify.model.OpponentList
import com.ricardofrango.squadify.model.UpdateOpponentRequest
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

class OpponentRepositoryImpl(
    private val httpClient: HttpClient,
    private val appConfig: AppConfig
) : OpponentRepository {

    override suspend fun getOpponents(clubId: String, ageGroupId: String?): List<Opponent> =
        httpClient.get("${appConfig.baseUrl}/opponents") {
            parameter("clubId", clubId)
            ageGroupId?.let { parameter("ageGroupId", it) }
        }.handleResponse()

    override suspend fun getOpponentList(clubId: String, ageGroupId: String?): OpponentList =
        httpClient.get("${appConfig.baseUrl}/opponents/list") {
            parameter("clubId", clubId)
            ageGroupId?.let { parameter("ageGroupId", it) }
        }.handleResponse()

    override suspend fun getOpponentDetails(opponentId: String): OpponentDetails =
        httpClient.get("${appConfig.baseUrl}/opponents/$opponentId").handleResponse()

    override suspend fun createOpponent(clubId: String, request: CreateOpponentRequest): Opponent =
        httpClient.post("${appConfig.baseUrl}/opponents") {
            parameter("clubId", clubId)
            contentType(ContentType.Application.Json)
            setBody(request)
        }.handleResponse()

    override suspend fun updateOpponent(opponentId: String, request: UpdateOpponentRequest) =
        httpClient.patch("${appConfig.baseUrl}/opponents/$opponentId") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.handleResponse<Unit>()
}
