package com.ricardofrango.clubmanager.data.remote

import com.ricardofrango.clubmanager.domain.config.AppConfig
import com.ricardofrango.clubmanager.domain.repository.ClubRepository
import com.ricardofrango.clubmanager.domain.repository.SessionRepository
import com.ricardofrango.clubmanager.model.Club
import com.ricardofrango.clubmanager.model.ClubDetailsResponse
import com.ricardofrango.clubmanager.model.ClubHome
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

class ClubRepositoryImpl(
    private val httpClient: HttpClient,
    private val appConfig: AppConfig,
    private val sessionRepository: SessionRepository
) : ClubRepository {
    
    override suspend fun getClubs(): List<Club> {
        val token = sessionRepository.getToken()
        return httpClient.get("${appConfig.baseUrl}/clubs") {
            token?.let {
                header(HttpHeaders.Authorization, "Bearer $it")
            }
        }.handleResponse()
    }

    override suspend fun getClubById(clubId: String): ClubDetailsResponse {
        val token = sessionRepository.getToken()
        return httpClient.get("${appConfig.baseUrl}/clubs/$clubId") {
            token?.let {
                header(HttpHeaders.Authorization, "Bearer $it")
            }
        }.handleResponse()
    }

    override suspend fun getClubHome(clubId: String): ClubHome {
        val token = sessionRepository.getToken()
        return httpClient.get("${appConfig.baseUrl}/clubs/$clubId/home") {
            token?.let {
                header(HttpHeaders.Authorization, "Bearer $it")
            }
        }.handleResponse()
    }
}
