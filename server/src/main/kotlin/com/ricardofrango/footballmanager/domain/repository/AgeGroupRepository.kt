package com.ricardofrango.footballmanager.domain.repository

import com.ricardofrango.footballmanager.model.AgeGroup

interface AgeGroupRepository {
    suspend fun getAgeGroupsByClub(clubId: String): List<AgeGroup>
    suspend fun createAgeGroup(ageGroup: AgeGroup): AgeGroup
    suspend fun createAgeGroups(ageGroups: List<AgeGroup>): List<AgeGroup>
    suspend fun getAgeGroupById(id: String): AgeGroup?
    suspend fun updateAgeGroup(ageGroup: AgeGroup): Boolean
    suspend fun findByDetails(clubId: String, seasonId: String, birthYear: Int): AgeGroup?
    suspend fun findByClubAndSeason(clubId: String, seasonId: String): List<AgeGroup>
}
