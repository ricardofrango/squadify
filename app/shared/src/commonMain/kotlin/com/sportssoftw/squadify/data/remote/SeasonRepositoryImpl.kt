package com.sportssoftw.squadify.data.remote

import com.sportssoftw.squadify.data.cache.cachedGet
import com.sportssoftw.squadify.domain.config.AppConfig
import com.sportssoftw.squadify.domain.repository.SeasonRepository
import com.sportssoftw.squadify.model.CreateSeasonRequest
import com.sportssoftw.squadify.model.Season
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

class SeasonRepositoryImpl(
    private val httpClient: HttpClient,
    private val appConfig: AppConfig
) : SeasonRepository {

    override suspend fun getSeasons(clubId: String): List<Season> =
        httpClient.cachedGet("${appConfig.baseUrl}/seasons") {
            parameter("clubId", clubId)
        }

    override suspend fun createSeason(request: CreateSeasonRequest): Season =
        httpClient.post("${appConfig.baseUrl}/seasons") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.handleResponse()
}
