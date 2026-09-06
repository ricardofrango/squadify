package com.ricardofrango.footballmanager.data.remote

import com.ricardofrango.footballmanager.domain.config.AppConfig
import com.ricardofrango.footballmanager.domain.repository.AgeGroupPlayerRepository
import com.ricardofrango.footballmanager.domain.repository.SessionRepository
import com.ricardofrango.footballmanager.model.AgeGroupPlayer
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

class AgeGroupPlayerRepositoryImpl(
    private val httpClient: HttpClient,
    private val appConfig: AppConfig,
    private val sessionRepository: SessionRepository
) : AgeGroupPlayerRepository {

    override suspend fun getAgeGroupPlayersByClub(clubId: String): List<AgeGroupPlayer> {
        val token = sessionRepository.getToken()
        return httpClient.get("${appConfig.baseUrl}/players/associations/age-group") {
            parameter("clubId", clubId)
            token?.let {
                header(HttpHeaders.Authorization, "Bearer $it")
            }
        }.handleResponse()
    }
}
