package com.sportssoftw.squadcall.domain.repository

import com.sportssoftw.squadcall.model.AddStaffToAgeGroupRequest
import com.sportssoftw.squadcall.model.AgeGroup
import com.sportssoftw.squadcall.model.AgeGroupDetails
import com.sportssoftw.squadcall.model.AgeGroupStaffMember
import com.sportssoftw.squadcall.model.CreateAgeGroupsRequest

interface AgeGroupRepository {
    suspend fun getAgeGroups(clubId: String): List<AgeGroup>
    suspend fun createAgeGroups(request: CreateAgeGroupsRequest): List<AgeGroup>
    suspend fun updateAgeGroup(ageGroupId: String, request: com.sportssoftw.squadcall.model.UpdateAgeGroupRequest)
    /** Deletes the age group for good, with its teams, games and training (and on the coach server its lone players). */
    suspend fun deleteAgeGroup(ageGroupId: String)
    suspend fun getAgeGroupDetails(ageGroupId: String): AgeGroupDetails
    suspend fun getAgeGroupSeasonRecord(ageGroupId: String): com.sportssoftw.squadcall.model.AgeGroupSeasonRecord
    suspend fun getAgeGroupFullStats(ageGroupId: String): com.sportssoftw.squadcall.model.FullStatsData
    suspend fun getAgeGroupStaff(ageGroupId: String): List<AgeGroupStaffMember>
    suspend fun addStaffToAgeGroup(ageGroupId: String, request: AddStaffToAgeGroupRequest)
    // The coach server only, for now (TECHNICAL_DECISIONS §72): the owner takes someone off the age group.
    suspend fun removeStaffFromAgeGroup(ageGroupId: String, userId: String)
}
