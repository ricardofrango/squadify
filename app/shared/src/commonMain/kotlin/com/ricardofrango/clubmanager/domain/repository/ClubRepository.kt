package com.ricardofrango.clubmanager.domain.repository

import com.ricardofrango.clubmanager.model.Club
import com.ricardofrango.clubmanager.model.ClubDetailsResponse
import com.ricardofrango.clubmanager.model.ClubHome
import com.ricardofrango.clubmanager.model.ClubMemberSummary

interface ClubRepository {
    suspend fun getClubs(): List<Club>
    suspend fun getClubById(clubId: String): ClubDetailsResponse
    suspend fun getClubHome(clubId: String): ClubHome
    suspend fun getClubMembers(clubId: String): List<ClubMemberSummary>
}
