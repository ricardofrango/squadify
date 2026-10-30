package com.ricardofrango.squadify.data.remote

import com.ricardofrango.squadify.domain.config.AppConfig
import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.model.AddStaffToAgeGroupRequest
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.AgeGroupDetails
import com.ricardofrango.squadify.model.AgeGroupSeasonRecord
import com.ricardofrango.squadify.model.AgeGroupStaffMember
import com.ricardofrango.squadify.model.CreateAgeGroupsRequest
import com.ricardofrango.squadify.model.UpdateAgeGroupRequest
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

class AgeGroupRepositoryImpl(
    private val httpClient: HttpClient,
    private val appConfig: AppConfig
) : AgeGroupRepository {

    override suspend fun getAgeGroups(clubId: String): List<AgeGroup> =
        httpClient.get("${appConfig.baseUrl}/age-groups") {
            parameter("clubId", clubId)
        }.handleResponse()

    override suspend fun createAgeGroups(request: CreateAgeGroupsRequest): List<AgeGroup> =
        httpClient.post("${appConfig.baseUrl}/age-groups") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.handleResponse()

    override suspend fun getAgeGroupSeasonRecord(ageGroupId: String): AgeGroupSeasonRecord =
        httpClient.get("${appConfig.baseUrl}/age-groups/$ageGroupId/season-record").handleResponse()

    override suspend fun getAgeGroupFullStats(ageGroupId: String): com.ricardofrango.squadify.model.FullStatsData =
        httpClient.get("${appConfig.baseUrl}/age-groups/$ageGroupId/full-stats").handleResponse()

    override suspend fun getAgeGroupDetails(ageGroupId: String): AgeGroupDetails =
        httpClient.get("${appConfig.baseUrl}/age-groups/$ageGroupId/details").handleResponse()

    override suspend fun getAgeGroupStaff(ageGroupId: String): List<AgeGroupStaffMember> =
        httpClient.get("${appConfig.baseUrl}/age-groups/$ageGroupId/staff").handleResponse()

    override suspend fun addStaffToAgeGroup(ageGroupId: String, request: AddStaffToAgeGroupRequest) {
        httpClient.post("${appConfig.baseUrl}/age-groups/$ageGroupId/staff") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.handleResponse<Unit>()
    }

    override suspend fun removeStaffFromAgeGroup(ageGroupId: String, userId: String) {
        httpClient.delete("${appConfig.baseUrl}/age-groups/$ageGroupId/staff/$userId").handleResponse<Unit>()
    }

    override suspend fun updateAgeGroup(ageGroupId: String, request: UpdateAgeGroupRequest) {
        httpClient.patch("${appConfig.baseUrl}/age-groups/$ageGroupId") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.handleResponse<Unit>()
    }
}
