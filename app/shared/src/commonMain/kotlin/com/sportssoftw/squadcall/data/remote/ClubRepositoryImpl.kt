package com.sportssoftw.squadcall.data.remote

import com.sportssoftw.squadcall.data.cache.cachedGet
import com.sportssoftw.squadcall.data.cache.CacheKind
import com.sportssoftw.squadcall.domain.config.AppConfig
import com.sportssoftw.squadcall.domain.repository.ClubRepository
import com.sportssoftw.squadcall.model.AddClubMembersRequest
import com.sportssoftw.squadcall.model.Club
import com.sportssoftw.squadcall.model.ClubDetailsResponse
import com.sportssoftw.squadcall.model.ClubHome
import com.sportssoftw.squadcall.model.ClubMemberSummary
import com.sportssoftw.squadcall.model.ClubMembersOverview
import com.sportssoftw.squadcall.model.ClubRole
import com.sportssoftw.squadcall.model.UpdateClubMemberRoleRequest
import com.sportssoftw.squadcall.model.ClubPermissionsResponse
import com.sportssoftw.squadcall.model.UpdateClubPermissionsRequest
import com.sportssoftw.squadcall.model.UpdateClubRequest
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.ByteArrayContent
import com.sportssoftw.squadcall.model.LogoResponse

class ClubRepositoryImpl(
    private val httpClient: HttpClient,
    private val appConfig: AppConfig
) : ClubRepository {

    override suspend fun getClubs(): List<Club> =
        httpClient.cachedGet("${appConfig.baseUrl}/clubs")

    override suspend fun getClubById(clubId: String): ClubDetailsResponse =
        httpClient.cachedGet("${appConfig.baseUrl}/clubs/$clubId")

    override suspend fun getClubHome(clubId: String): ClubHome =
        httpClient.cachedGet("${appConfig.baseUrl}/clubs/$clubId/home", CacheKind.MATCH)

    override suspend fun getClubMembers(clubId: String): List<ClubMemberSummary> =
        httpClient.cachedGet("${appConfig.baseUrl}/clubs/$clubId/members")

    override suspend fun addClubMembers(clubId: String, request: AddClubMembersRequest) {
        httpClient.post("${appConfig.baseUrl}/clubs/$clubId/members") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.handleResponse<Unit>()
    }

    override suspend fun getClubMembersPage(
        clubId: String,
        role: ClubRole?,
        query: String,
        userId: String?,
        offset: Int,
        limit: Int
    ): ClubMembersOverview =
        httpClient.cachedGet("${appConfig.baseUrl}/clubs/$clubId/members/overview") {
            role?.let { parameter("role", it.name) }
            query.trim().takeIf { it.isNotEmpty() }?.let { parameter("q", it) }
            userId?.let { parameter("userId", it) }
            parameter("limit", limit)
            parameter("offset", offset)
        }

    override suspend fun updateClubMemberRole(clubId: String, userId: String, role: ClubRole) {
        httpClient.patch("${appConfig.baseUrl}/clubs/$clubId/members/$userId") {
            contentType(ContentType.Application.Json)
            setBody(UpdateClubMemberRoleRequest(role))
        }.handleResponse<Unit>()
    }

    override suspend fun removeClubMember(clubId: String, userId: String) {
        httpClient.delete("${appConfig.baseUrl}/clubs/$clubId/members/$userId").handleResponse<Unit>()
    }

    override suspend fun resendClubInvite(clubId: String, inviteId: String) {
        httpClient.post("${appConfig.baseUrl}/clubs/$clubId/invites/$inviteId/resend").handleResponse<Unit>()
    }

    override suspend fun cancelClubInvite(clubId: String, inviteId: String) {
        httpClient.delete("${appConfig.baseUrl}/clubs/$clubId/invites/$inviteId").handleResponse<Unit>()
    }

    override suspend fun updateClub(clubId: String, request: UpdateClubRequest) {
        httpClient.patch("${appConfig.baseUrl}/clubs/$clubId") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.handleResponse<Unit>()
    }

    override suspend fun uploadClubLogo(clubId: String, bytes: ByteArray): String =
        httpClient.put("${appConfig.baseUrl}/clubs/$clubId/logo") {
            setBody(ByteArrayContent(bytes, ContentType.Application.OctetStream))
        }.handleResponse<LogoResponse>().logoUrl

    override suspend fun deleteClubLogo(clubId: String) {
        httpClient.delete("${appConfig.baseUrl}/clubs/$clubId/logo").handleResponse<Unit>()
    }

    override suspend fun getClubPermissions(clubId: String): ClubPermissionsResponse =
        httpClient.cachedGet("${appConfig.baseUrl}/clubs/$clubId/permissions")

    override suspend fun updateClubPermissions(clubId: String, request: UpdateClubPermissionsRequest) {
        httpClient.patch("${appConfig.baseUrl}/clubs/$clubId/permissions") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.handleResponse<Unit>()
    }
}
