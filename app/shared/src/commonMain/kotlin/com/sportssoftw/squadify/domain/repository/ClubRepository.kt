package com.sportssoftw.squadify.domain.repository

import com.sportssoftw.squadify.model.ClubMembersOverview
import com.sportssoftw.squadify.model.ClubRole
import com.sportssoftw.squadify.model.Paging
import com.sportssoftw.squadify.model.AddClubMembersRequest
import com.sportssoftw.squadify.model.Club
import com.sportssoftw.squadify.model.ClubDetailsResponse
import com.sportssoftw.squadify.model.ClubHome
import com.sportssoftw.squadify.model.ClubMemberSummary
import com.sportssoftw.squadify.model.ClubPermissionsResponse
import com.sportssoftw.squadify.model.UpdateClubPermissionsRequest
import com.sportssoftw.squadify.model.UpdateClubRequest

interface ClubRepository {
    suspend fun getClubs(): List<Club>
    suspend fun getClubById(clubId: String): ClubDetailsResponse
    suspend fun getClubHome(clubId: String): ClubHome
    suspend fun getClubMembers(clubId: String): List<ClubMemberSummary>
    suspend fun addClubMembers(clubId: String, request: AddClubMembersRequest)
    // One page of Club settings → Members ([role] and [query] narrow it on the server), or just
    // [userId] (Member details); the pending invites and role counts come whole.
    suspend fun getClubMembersPage(
        clubId: String,
        role: ClubRole?,
        query: String,
        userId: String?,
        offset: Int,
        limit: Int = Paging.PAGE_SIZE
    ): ClubMembersOverview
    suspend fun updateClubMemberRole(clubId: String, userId: String, role: ClubRole)
    suspend fun removeClubMember(clubId: String, userId: String)
    suspend fun resendClubInvite(clubId: String, inviteId: String)
    suspend fun cancelClubInvite(clubId: String, inviteId: String)
    suspend fun updateClub(clubId: String, request: UpdateClubRequest)
    /** Uploads [bytes] (PNG or JPEG) as the club's logo; returns the new logo path. */
    suspend fun uploadClubLogo(clubId: String, bytes: ByteArray): String
    suspend fun deleteClubLogo(clubId: String)
    suspend fun getClubPermissions(clubId: String): ClubPermissionsResponse
    suspend fun updateClubPermissions(clubId: String, request: UpdateClubPermissionsRequest)
}
