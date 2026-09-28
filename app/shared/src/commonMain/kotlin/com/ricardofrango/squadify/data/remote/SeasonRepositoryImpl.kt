package com.ricardofrango.squadify.data.remote

import com.ricardofrango.squadify.domain.config.AppConfig
import com.ricardofrango.squadify.domain.repository.SeasonRepository
import com.ricardofrango.squadify.model.CreateSeasonRequest
import com.ricardofrango.squadify.model.Season
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

class SeasonRepositoryImpl(
    private val httpClient: HttpClient,
    private val appConfig: AppConfig
) : SeasonRepository {

    override suspend fun getSeasons(clubId: String): List<Season> =
        httpClient.get("${appConfig.baseUrl}/seasons") {
            parameter("clubId", clubId)
        }.handleResponse()

    override suspend fun createSeason(request: CreateSeasonRequest): Season =
        httpClient.post("${appConfig.baseUrl}/seasons") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.handleResponse()
}
