package com.sportssoftw.squadcall.data.remote

import com.sportssoftw.squadcall.data.cache.cachedGet
import com.sportssoftw.squadcall.data.cache.CacheKind
import com.sportssoftw.squadcall.domain.config.AppConfig
import com.sportssoftw.squadcall.domain.repository.AgeGroupRepository
import com.sportssoftw.squadcall.model.AddStaffToAgeGroupRequest
import com.sportssoftw.squadcall.model.AgeGroup
import com.sportssoftw.squadcall.model.AgeGroupDetails
import com.sportssoftw.squadcall.model.AgeGroupSeasonRecord
import com.sportssoftw.squadcall.model.AgeGroupStaffMember
import com.sportssoftw.squadcall.model.CreateAgeGroupsRequest
import com.sportssoftw.squadcall.model.UpdateAgeGroupRequest
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

class AgeGroupRepositoryImpl(
    private val httpClient: HttpClient,
    private val appConfig: AppConfig
) : AgeGroupRepository {

    override suspend fun getAgeGroups(clubId: String): List<AgeGroup> =
        httpClient.cachedGet("${appConfig.baseUrl}/age-groups") {
            parameter("clubId", clubId)
        }

    override suspend fun createAgeGroups(request: CreateAgeGroupsRequest): List<AgeGroup> =
        httpClient.post("${appConfig.baseUrl}/age-groups") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.handleResponse()

    override suspend fun getAgeGroupSeasonRecord(ageGroupId: String): AgeGroupSeasonRecord =
        httpClient.cachedGet("${appConfig.baseUrl}/age-groups/$ageGroupId/season-record", CacheKind.MATCH)

    override suspend fun getAgeGroupFullStats(ageGroupId: String): com.sportssoftw.squadcall.model.FullStatsData =
        httpClient.cachedGet("${appConfig.baseUrl}/age-groups/$ageGroupId/full-stats", CacheKind.MATCH)

    override suspend fun getAgeGroupDetails(ageGroupId: String): AgeGroupDetails =
        httpClient.cachedGet("${appConfig.baseUrl}/age-groups/$ageGroupId/details", CacheKind.MATCH)

    override suspend fun getAgeGroupStaff(ageGroupId: String): List<AgeGroupStaffMember> =
        httpClient.cachedGet("${appConfig.baseUrl}/age-groups/$ageGroupId/staff")

    override suspend fun addStaffToAgeGroup(ageGroupId: String, request: AddStaffToAgeGroupRequest) {
        httpClient.post("${appConfig.baseUrl}/age-groups/$ageGroupId/staff") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.handleResponse<Unit>()
    }

    override suspend fun removeStaffFromAgeGroup(ageGroupId: String, userId: String) {
        httpClient.delete("${appConfig.baseUrl}/age-groups/$ageGroupId/staff/$userId").handleResponse<Unit>()
    }

    override suspend fun deleteAgeGroup(ageGroupId: String) {
        httpClient.delete("${appConfig.baseUrl}/age-groups/$ageGroupId").handleResponse<Unit>()
    }

    override suspend fun updateAgeGroup(ageGroupId: String, request: UpdateAgeGroupRequest) {
        httpClient.patch("${appConfig.baseUrl}/age-groups/$ageGroupId") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.handleResponse<Unit>()
    }
}
