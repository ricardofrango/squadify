package com.ricardofrango.squadify.coach.data

import com.ricardofrango.squadify.coach.domain.SpaceRepository
import com.ricardofrango.squadify.coach.domain.StaffInviteRepository
import com.ricardofrango.squadify.data.remote.handleResponse
import com.ricardofrango.squadify.domain.config.AppConfig
import com.ricardofrango.squadify.model.CoachSpace
import com.ricardofrango.squadify.model.CreateStaffInviteRequest
import com.ricardofrango.squadify.model.GooglePurchaseRequest
import com.ricardofrango.squadify.model.PlansResponse
import com.ricardofrango.squadify.model.RequestPlanRequest
import com.ricardofrango.squadify.model.SpacePlan
import com.ricardofrango.squadify.model.SetUpSpaceRequest
import com.ricardofrango.squadify.model.SpaceAgeGroups
import com.ricardofrango.squadify.model.SpaceSetup
import com.ricardofrango.squadify.model.StaffInvite
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
        httpClient.get("${appConfig.baseUrl}/spaces").handleResponse()

    // The features come written by the server, in the app's language (its Accept-Language).
    override suspend fun getPlans(): PlansResponse =
        httpClient.get("${appConfig.baseUrl}/plans").handleResponse()

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

    override suspend fun setUpSpace(request: SetUpSpaceRequest): SpaceSetup =
        httpClient.post("${appConfig.baseUrl}/space") {
            contentType(ContentType.Application.Json)
            setBody(request)
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
