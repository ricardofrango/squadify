package com.ricardofrango.footballmanager.domain.repository

import com.ricardofrango.footballmanager.model.AgeGroup
import com.ricardofrango.footballmanager.model.AgeGroupStaff
import com.ricardofrango.footballmanager.model.AgeGroupStaffMember
import com.ricardofrango.footballmanager.model.TeamRole

interface AgeGroupRepository {
    suspend fun getAgeGroupsByClub(clubId: String): List<AgeGroup>
    suspend fun createAgeGroup(ageGroup: AgeGroup): AgeGroup
    suspend fun createAgeGroups(ageGroups: List<AgeGroup>): List<AgeGroup>
    suspend fun getAgeGroupById(id: String): AgeGroup?
    suspend fun updateAgeGroup(ageGroup: AgeGroup): Boolean
    suspend fun findByDetails(clubId: String, seasonId: String, birthYear: Int): AgeGroup?
    suspend fun findByClubAndSeason(clubId: String, seasonId: String): List<AgeGroup>

    // Age-group staff (coaches / support staff). Teams no longer carry their own staff.
    suspend fun getUserRoleInAgeGroup(userId: String, ageGroupId: String): TeamRole?
    suspend fun addStaffToAgeGroup(staff: List<AgeGroupStaff>): Boolean
    suspend fun getAgeGroupStaff(ageGroupId: String): List<AgeGroupStaffMember>
    suspend fun countCoachesByClub(clubId: String): Long
}
