package com.ricardofrango.squadify.domain.repository

import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.model.ClubMemberSummary
import com.ricardofrango.squadify.model.ClubRole
import kotlin.time.Instant

/** Which of the club's members a page of Club settings → Members holds. Every set filter applies. */
data class MemberListFilter(
    val role: ClubRole? = null,
    // Already folded with SearchText.key; found in "first last" or in the email.
    val search: String? = null,
    // Just this one member (Member details).
    val userId: String? = null
)

/** An active club member with their account, as Club settings → Members lists them. */
data class ClubMemberAccount(
    val userId: String,
    val firstName: String,
    val lastName: String,
    val email: String,
    val phone: String?,
    val role: ClubRole,
    val accountCreatedAt: Instant,
    val avatarUrl: String?
)

interface ClubRepository {
    suspend fun getAllClubs(): List<Club>
    suspend fun getClubsByUser(userId: String): List<Club>
    suspend fun createClub(club: Club): Club
    suspend fun getClubById(id: String): Club?
    suspend fun updateClub(club: Club): Boolean
    /** Sets (or clears, with null) only the club's logo path. */
    suspend fun updateLogoUrl(clubId: String, logoUrl: String?): Boolean
    suspend fun addMember(clubId: String, userId: String, role: ClubRole): Boolean
    suspend fun addMembers(clubId: String, userIds: List<Pair<String, ClubRole>>): Boolean
    suspend fun getClubMembers(clubId: String): List<ClubMemberSummary>
    // One page of the club's active members matching [filter]: admins, then coordinators, then
    // members, each by name.
    suspend fun getClubMembersPage(clubId: String, filter: MemberListFilter, limit: Int, offset: Int): List<ClubMemberAccount>
    // How many active members hold each role, those matching [search] only when it's given.
    suspend fun countClubMembersByRole(clubId: String, search: String? = null): Map<ClubRole, Int>
    suspend fun updateMemberRole(clubId: String, userId: String, role: ClubRole): Boolean
    /** Deletes the membership row; returns whether there was one. */
    suspend fun removeMember(clubId: String, userId: String): Boolean
    suspend fun countAllClubs(): Long
    suspend fun getRecentClubs(limit: Int): List<Club>
}
