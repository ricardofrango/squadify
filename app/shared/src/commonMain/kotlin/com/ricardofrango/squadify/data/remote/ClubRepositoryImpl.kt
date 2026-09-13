package com.ricardofrango.squadify.data.remote

import com.ricardofrango.squadify.domain.config.AppConfig
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.SessionRepository
import com.ricardofrango.squadify.model.AddClubMembersRequest
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.model.ClubDetailsResponse
import com.ricardofrango.squadify.model.ClubHome
import com.ricardofrango.squadify.model.ClubMemberSummary
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

    override suspend fun getClubMembers(clubId: String): List<ClubMemberSummary> {
        val token = sessionRepository.getToken()
        return httpClient.get("${appConfig.baseUrl}/clubs/$clubId/members") {
            token?.let {
                header(HttpHeaders.Authorization, "Bearer $it")
            }
        }.handleResponse()
    }

    override suspend fun addClubMembers(clubId: String, request: AddClubMembersRequest) {
        val token = sessionRepository.getToken()
        httpClient.post("${appConfig.baseUrl}/clubs/$clubId/members") {
            contentType(ContentType.Application.Json)
            setBody(request)
            token?.let {
                header(HttpHeaders.Authorization, "Bearer $it")
            }
        }.handleResponse<Unit>()
    }
}
