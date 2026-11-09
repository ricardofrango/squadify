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
    /**
     * Deletes the age group and everything in it: its teams with all their games (and theirs), its training,
     * staff and pool; with [deleteLonePlayers], also its players who are in no other age group and have
     * nothing else left (the coach's space, which has no roster outside its age groups).
     */
    suspend fun deleteAgeGroup(ageGroupId: String, deleteLonePlayers: Boolean): DeletedAgeGroup

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

/**
 * What deleting an age group took: its games' ids (to tell open screens) and the images left to remove from
 * storage: its crest's path, and each deleted player's id with their photo's path.
 */
data class DeletedAgeGroup(
    val matchIds: List<String>,
    val crestUrl: String?,
    val deletedPlayers: List<Pair<String, String?>>
)
