package com.ricardofrango.squadify.domain.repository

import com.ricardofrango.squadify.model.ClubMembersOverview
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.AddClubMembersRequest
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.model.ClubDetailsResponse
import com.ricardofrango.squadify.model.ClubHome
import com.ricardofrango.squadify.model.ClubMemberSummary
import com.ricardofrango.squadify.model.ClubPermissionsResponse
import com.ricardofrango.squadify.model.UpdateClubPermissionsRequest
import com.ricardofrango.squadify.model.UpdateClubRequest

interface ClubRepository {
    suspend fun getClubs(): List<Club>
    suspend fun getClubById(clubId: String): ClubDetailsResponse
    suspend fun getClubHome(clubId: String): ClubHome
    suspend fun getClubMembers(clubId: String): List<ClubMemberSummary>
    suspend fun addClubMembers(clubId: String, request: AddClubMembersRequest)
    suspend fun getClubMembersOverview(clubId: String): ClubMembersOverview
    suspend fun updateClubMemberRole(clubId: String, userId: String, role: ClubRole)
    suspend fun removeClubMember(clubId: String, userId: String)
    suspend fun resendClubInvite(clubId: String, inviteId: String)
    suspend fun cancelClubInvite(clubId: String, inviteId: String)
    suspend fun updateClub(clubId: String, request: UpdateClubRequest)
    suspend fun getClubPermissions(clubId: String): ClubPermissionsResponse
    suspend fun updateClubPermissions(clubId: String, request: UpdateClubPermissionsRequest)
}
