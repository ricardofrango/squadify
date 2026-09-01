package com.ricardofrango.footballmanager.data.remote

import com.ricardofrango.footballmanager.domain.config.AppConfig
import com.ricardofrango.footballmanager.domain.repository.SeasonRepository
import com.ricardofrango.footballmanager.domain.repository.SessionRepository
import com.ricardofrango.footballmanager.model.Season
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

class SeasonRepositoryImpl(
    private val httpClient: HttpClient,
    private val appConfig: AppConfig,
    private val sessionRepository: SessionRepository
) : SeasonRepository {

    override suspend fun getSeasons(clubId: String): List<Season> {
        val token = sessionRepository.getToken()
        return httpClient.get("${appConfig.baseUrl}/seasons") {
            parameter("clubId", clubId)
            token?.let {
                header(HttpHeaders.Authorization, "Bearer $it")
            }
        }.handleResponse()
    }
}
