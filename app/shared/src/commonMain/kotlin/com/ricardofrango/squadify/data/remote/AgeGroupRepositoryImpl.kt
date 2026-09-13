package com.ricardofrango.squadify.data.remote

import com.ricardofrango.squadify.domain.config.AppConfig
import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.SessionRepository
import com.ricardofrango.squadify.model.AddStaffToAgeGroupRequest
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.AgeGroupDetails
import com.ricardofrango.squadify.model.AgeGroupStaffMember
import com.ricardofrango.squadify.model.CreateAgeGroupsRequest
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

    override suspend fun getAgeGroupStaff(ageGroupId: String): List<AgeGroupStaffMember> {
        val token = sessionRepository.getToken()
        return httpClient.get("${appConfig.baseUrl}/age-groups/$ageGroupId/staff") {
            token?.let {
                header(HttpHeaders.Authorization, "Bearer $it")
            }
        }.handleResponse()
    }

    override suspend fun addStaffToAgeGroup(ageGroupId: String, request: AddStaffToAgeGroupRequest) {
        val token = sessionRepository.getToken()
        httpClient.post("${appConfig.baseUrl}/age-groups/$ageGroupId/staff") {
            contentType(ContentType.Application.Json)
            setBody(request)
            token?.let {
                header(HttpHeaders.Authorization, "Bearer $it")
            }
        }.handleResponse<Unit>()
    }
}
