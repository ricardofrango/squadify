package com.ricardofrango.squadify.data.remote

import com.ricardofrango.squadify.domain.config.AppConfig
import com.ricardofrango.squadify.domain.repository.OpponentRepository
import com.ricardofrango.squadify.model.CreateOpponentRequest
import com.ricardofrango.squadify.model.Opponent
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

    override suspend fun createOpponent(clubId: String, request: CreateOpponentRequest): Opponent =
        httpClient.post("${appConfig.baseUrl}/opponents") {
            parameter("clubId", clubId)
            contentType(ContentType.Application.Json)
            setBody(request)
        }.handleResponse()
}
