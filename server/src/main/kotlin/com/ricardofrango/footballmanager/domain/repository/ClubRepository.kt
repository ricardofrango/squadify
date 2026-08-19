package com.ricardofrango.footballmanager.domain.repository

import com.ricardofrango.footballmanager.model.Club
import com.ricardofrango.footballmanager.model.Role

interface ClubRepository {
    suspend fun getAllClubs(): List<Club>
    suspend fun getClubsByUser(userId: String): List<Club>
    suspend fun createClub(club: Club): Club
    suspend fun getClubById(id: String): Club?
    suspend fun addMember(clubId: String, userId: String, role: Role): Boolean
}
