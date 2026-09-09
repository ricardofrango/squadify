package com.ricardofrango.clubmanager.data.remote

import com.ricardofrango.clubmanager.domain.config.AppConfig
import com.ricardofrango.clubmanager.domain.repository.AgeGroupRepository
import com.ricardofrango.clubmanager.domain.repository.SessionRepository
import com.ricardofrango.clubmanager.model.AgeGroup
import com.ricardofrango.clubmanager.model.AgeGroupDetails
import com.ricardofrango.clubmanager.model.CreateAgeGroupsRequest
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

class AgeGroupRepositoryImpl(
    private val httpClient: HttpClient,
    private val appConfig: AppConfig,
    private val sessionRepository: SessionRepository
) : AgeGroupRepository {

    override suspend fun getAgeGroups(clubId: String): List<AgeGroup> {
        val token = sessionRepository.getToken()
        return httpClient.get("${appConfig.baseUrl}/age-groups") {
            parameter("clubId", clubId)
            token?.let {
                header(HttpHeaders.Authorization, "Bearer $it")
            }
        }.handleResponse()
    }

    override suspend fun createAgeGroups(request: CreateAgeGroupsRequest): List<AgeGroup> {
        val token = sessionRepository.getToken()
        return httpClient.post("${appConfig.baseUrl}/age-groups") {
            contentType(ContentType.Application.Json)
            setBody(request)
            token?.let {
                header(HttpHeaders.Authorization, "Bearer $it")
            }
        }.handleResponse()
    }

    override suspend fun getAgeGroupDetails(ageGroupId: String): AgeGroupDetails {
        val token = sessionRepository.getToken()
        return httpClient.get("${appConfig.baseUrl}/age-groups/$ageGroupId/details") {
            token?.let {
                header(HttpHeaders.Authorization, "Bearer $it")
            }
        }.handleResponse()
    }
}
