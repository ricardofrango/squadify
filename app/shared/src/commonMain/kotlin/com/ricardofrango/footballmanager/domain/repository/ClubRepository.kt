package com.ricardofrango.footballmanager.domain.repository

import com.ricardofrango.footballmanager.model.Club
import com.ricardofrango.footballmanager.model.ClubDetailsResponse
import com.ricardofrango.footballmanager.model.ClubHome

interface ClubRepository {
    suspend fun getClubs(): List<Club>
    suspend fun getClubById(clubId: String): ClubDetailsResponse
    suspend fun getClubHome(clubId: String): ClubHome
}
