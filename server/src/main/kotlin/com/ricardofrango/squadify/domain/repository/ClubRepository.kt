package com.ricardofrango.squadify.domain.repository

import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.model.ClubMemberSummary
import com.ricardofrango.squadify.model.ClubRole

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
    suspend fun updateMemberRole(clubId: String, userId: String, role: ClubRole): Boolean
    /** Deletes the membership row; returns whether there was one. */
    suspend fun removeMember(clubId: String, userId: String): Boolean
    suspend fun countAllClubs(): Long
    suspend fun getRecentClubs(limit: Int): List<Club>
}
