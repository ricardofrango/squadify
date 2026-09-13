package com.ricardofrango.squadify.domain.repository

import com.ricardofrango.squadify.model.AddClubMembersRequest
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.model.ClubDetailsResponse
import com.ricardofrango.squadify.model.ClubHome
import com.ricardofrango.squadify.model.ClubMemberSummary

interface ClubRepository {
    suspend fun getClubs(): List<Club>
    suspend fun getClubById(clubId: String): ClubDetailsResponse
    suspend fun getClubHome(clubId: String): ClubHome
    suspend fun getClubMembers(clubId: String): List<ClubMemberSummary>
    suspend fun addClubMembers(clubId: String, request: AddClubMembersRequest)
}
