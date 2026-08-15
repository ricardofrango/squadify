package com.ricardofrango.footballmanager.domain.repository

import com.ricardofrango.footballmanager.model.Club

interface ClubRepository {
    suspend fun getAllClubs(): List<Club>
    suspend fun createClub(club: Club): Boolean
    suspend fun getClubById(id: String): Club?
}
