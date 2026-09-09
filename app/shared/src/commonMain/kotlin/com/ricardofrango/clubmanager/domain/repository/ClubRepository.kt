package com.ricardofrango.clubmanager.domain.repository

import com.ricardofrango.clubmanager.model.Club
import com.ricardofrango.clubmanager.model.ClubDetailsResponse
import com.ricardofrango.clubmanager.model.ClubHome

interface ClubRepository {
    suspend fun getClubs(): List<Club>
    suspend fun getClubById(clubId: String): ClubDetailsResponse
    suspend fun getClubHome(clubId: String): ClubHome
}
