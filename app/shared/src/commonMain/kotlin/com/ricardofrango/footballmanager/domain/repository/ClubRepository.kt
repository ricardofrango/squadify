package com.ricardofrango.footballmanager.domain.repository

import com.ricardofrango.footballmanager.model.Club

interface ClubRepository {
    suspend fun getClubs(): List<Club>
    suspend fun getClubById(clubId: String): Club
}
