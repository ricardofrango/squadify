package com.ricardofrango.squadify.data.remote

import com.ricardofrango.squadify.domain.config.AppConfig
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.model.AddClubMembersRequest
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.model.ClubDetailsResponse
import com.ricardofrango.squadify.model.ClubHome
import com.ricardofrango.squadify.model.ClubMemberSummary
import com.ricardofrango.squadify.model.ClubPermissionsResponse
import com.ricardofrango.squadify.model.UpdateClubPermissionsRequest
import com.ricardofrango.squadify.model.UpdateClubRequest
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

class ClubRepositoryImpl(
    private val httpClient: HttpClient,
    private val appConfig: AppConfig
) : ClubRepository {

    override suspend fun getClubs(): List<Club> =
        httpClient.get("${appConfig.baseUrl}/clubs").handleResponse()

    override suspend fun getClubById(clubId: String): ClubDetailsResponse =
        httpClient.get("${appConfig.baseUrl}/clubs/$clubId").handleResponse()

    override suspend fun getClubHome(clubId: String): ClubHome =
        httpClient.get("${appConfig.baseUrl}/clubs/$clubId/home").handleResponse()

    override suspend fun getClubMembers(clubId: String): List<ClubMemberSummary> =
        httpClient.get("${appConfig.baseUrl}/clubs/$clubId/members").handleResponse()

    override suspend fun addClubMembers(clubId: String, request: AddClubMembersRequest) {
        httpClient.post("${appConfig.baseUrl}/clubs/$clubId/members") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.handleResponse<Unit>()
    }

    override suspend fun updateClub(clubId: String, request: UpdateClubRequest) {
        httpClient.patch("${appConfig.baseUrl}/clubs/$clubId") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.handleResponse<Unit>()
    }

    override suspend fun getClubPermissions(clubId: String): ClubPermissionsResponse =
        httpClient.get("${appConfig.baseUrl}/clubs/$clubId/permissions").handleResponse()

    override suspend fun updateClubPermissions(clubId: String, request: UpdateClubPermissionsRequest) {
        httpClient.patch("${appConfig.baseUrl}/clubs/$clubId/permissions") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.handleResponse<Unit>()
    }
}
