package com.sportssoftw.squadify.coach.data

import com.sportssoftw.squadify.data.cache.cachedGet
import com.sportssoftw.squadify.data.cache.CacheKind
import com.sportssoftw.squadify.coach.domain.SpaceRepository
import com.sportssoftw.squadify.coach.domain.StaffInviteRepository
import com.sportssoftw.squadify.data.remote.handleResponse
import com.sportssoftw.squadify.domain.config.AppConfig
import com.sportssoftw.squadify.model.CoachSpace
import com.sportssoftw.squadify.model.CreateStaffInviteRequest
import com.sportssoftw.squadify.model.DeleteSpaceRequest
import com.sportssoftw.squadify.model.LeaveSpaceRequest
import com.sportssoftw.squadify.model.GooglePurchaseRequest
import com.sportssoftw.squadify.model.PlansResponse
import com.sportssoftw.squadify.model.RequestPlanRequest
import com.sportssoftw.squadify.model.SpacePlan
import com.sportssoftw.squadify.model.SpaceAgeGroups
import com.sportssoftw.squadify.model.SpaceDetails
import com.sportssoftw.squadify.model.AddAgeGroupsRequest
import com.sportssoftw.squadify.model.AddedAgeGroups
import com.sportssoftw.squadify.model.LogoResponse
import io.ktor.client.request.put
import io.ktor.client.statement.readRawBytes
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.isSuccess
import com.sportssoftw.squadify.model.StaffInvite
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class SpaceRepositoryImpl(
    private val httpClient: HttpClient,
    private val appConfig: AppConfig
) : SpaceRepository {

    override suspend fun getSpaces(): List<SpaceAgeGroups> =
        httpClient.cachedGet("${appConfig.baseUrl}/spaces")

    override suspend fun deleteSpace(spaceId: String, currentPassword: String) {
        httpClient.post("${appConfig.baseUrl}/spaces/$spaceId/delete") {
            contentType(ContentType.Application.Json)
            setBody(DeleteSpaceRequest(currentPassword))
        }.handleResponse<Unit>()
    }

    override suspend fun leaveSpace(spaceId: String, currentPassword: String) {
        httpClient.post("${appConfig.baseUrl}/spaces/$spaceId/leave") {
            contentType(ContentType.Application.Json)
            setBody(LeaveSpaceRequest(currentPassword))
        }.handleResponse<Unit>()
    }

    override suspend fun getSpaceDetails(spaceId: String): SpaceDetails =
        httpClient.cachedGet("${appConfig.baseUrl}/spaces/$spaceId", CacheKind.MATCH)

    override suspend fun checkAgeGroupRoom(spaceId: String) {
        httpClient.get("${appConfig.baseUrl}/spaces/$spaceId/age-groups/room").handleResponse<Unit>()
    }

    override suspend fun addAgeGroups(request: AddAgeGroupsRequest): AddedAgeGroups =
        httpClient.post("${appConfig.baseUrl}/space/age-groups") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.handleResponse()

    override suspend fun uploadAgeGroupCrest(ageGroupId: String, bytes: ByteArray): String =
        httpClient.put("${appConfig.baseUrl}/age-groups/$ageGroupId/logo") {
            setBody(ByteArrayContent(bytes, ContentType.Application.OctetStream))
        }.handleResponse<LogoResponse>().logoUrl

    override suspend fun imageBytes(path: String): ByteArray {
        val response = httpClient.get(appConfig.baseUrl + path)
        if (!response.status.isSuccess()) throw IllegalStateException("Image not found: ${response.status}")
        return response.readRawBytes()
    }

    // The features come written by the server, in the app's language (its Accept-Language).
    override suspend fun getPlans(): PlansResponse =
        httpClient.cachedGet("${appConfig.baseUrl}/plans")

    override suspend fun verifyGooglePurchase(productId: String, purchaseToken: String): PlansResponse =
        httpClient.post("${appConfig.baseUrl}/plans/purchases/google") {
            contentType(ContentType.Application.Json)
            setBody(GooglePurchaseRequest(productId, purchaseToken))
        }.handleResponse()

    override suspend fun requestPlan(plan: SpacePlan): PlansResponse =
        httpClient.post("${appConfig.baseUrl}/plans/request") {
            contentType(ContentType.Application.Json)
            setBody(RequestPlanRequest(plan))
        }.handleResponse()

    override suspend fun acceptInvite(code: String): CoachSpace =
        httpClient.post("${appConfig.baseUrl}/invites/$code/accept").handleResponse()
}

class StaffInviteRepositoryImpl(
    private val httpClient: HttpClient,
    private val appConfig: AppConfig
) : StaffInviteRepository {

    override suspend fun getInvites(ageGroupId: String): List<StaffInvite> =
        httpClient.get("${appConfig.baseUrl}/age-groups/$ageGroupId/invites").handleResponse()

    override suspend fun createInvite(ageGroupId: String, request: CreateStaffInviteRequest): StaffInvite =
        httpClient.post("${appConfig.baseUrl}/age-groups/$ageGroupId/invites") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.handleResponse()

    override suspend fun revokeInvite(ageGroupId: String, code: String) {
        httpClient.delete("${appConfig.baseUrl}/age-groups/$ageGroupId/invites/$code").handleResponse<Unit>()
    }
}
