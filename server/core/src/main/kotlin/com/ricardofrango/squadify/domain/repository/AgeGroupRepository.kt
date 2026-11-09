package com.ricardofrango.squadify.domain.repository

import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.AgeGroupStaff
import com.ricardofrango.squadify.model.AgeGroupStaffMember
import com.ricardofrango.squadify.model.AgeGroupRole

interface AgeGroupRepository {
    suspend fun getAgeGroupsByClub(clubId: String): List<AgeGroup>
    suspend fun createAgeGroup(ageGroup: AgeGroup): AgeGroup
    suspend fun createAgeGroups(ageGroups: List<AgeGroup>): List<AgeGroup>
    suspend fun getAgeGroupById(id: String): AgeGroup?
    suspend fun updateAgeGroup(ageGroup: AgeGroup): Boolean
    /** The age group's crest (`AgeGroupLogos`); null removes it. */
    suspend fun updateLogoUrl(ageGroupId: String, logoUrl: String?): Boolean
    suspend fun findByDetails(clubId: String, seasonId: String, startYear: Int, endYear: Int): AgeGroup?
    suspend fun findByClubAndSeason(clubId: String, seasonId: String): List<AgeGroup>

    // Age-group staff (coaches / support staff). Teams no longer carry their own staff.
    suspend fun getUserRoleInAgeGroup(userId: String, ageGroupId: String): AgeGroupRole?
    suspend fun addStaffToAgeGroup(staff: List<AgeGroupStaff>): Boolean
    suspend fun getAgeGroupStaff(ageGroupId: String): List<AgeGroupStaffMember>
    /** Drops [userId] from the staff of every age group of [clubId], in every season; returns how many rows went. */
    suspend fun removeStaffFromClub(clubId: String, userId: String): Int
    /** Deletes [userId]'s staff row on [ageGroupId]; returns whether there was one. */
    suspend fun removeStaffFromAgeGroup(ageGroupId: String, userId: String): Boolean
    suspend fun countCoachesByClub(clubId: String): Long
}
