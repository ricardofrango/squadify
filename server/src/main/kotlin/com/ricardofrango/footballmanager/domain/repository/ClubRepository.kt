package com.ricardofrango.footballmanager.domain.repository

import com.ricardofrango.footballmanager.model.Club
import com.ricardofrango.footballmanager.model.ClubRole

interface ClubRepository {
    suspend fun getAllClubs(): List<Club>
    suspend fun getClubsByUser(userId: String): List<Club>
    suspend fun createClub(club: Club): Club
    suspend fun getClubById(id: String): Club?
    suspend fun updateClub(club: Club): Boolean
    suspend fun addMember(clubId: String, userId: String, role: ClubRole): Boolean
    suspend fun addMembers(clubId: String, userIds: List<Pair<String, ClubRole>>): Boolean
    suspend fun updateMemberRole(clubId: String, userId: String, role: ClubRole): Boolean
    suspend fun countAllClubs(): Long
    suspend fun getRecentClubs(limit: Int): List<Club>
}
