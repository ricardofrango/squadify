package com.ricardofrango.squadify.domain.repository

import com.ricardofrango.squadify.model.AddStaffToAgeGroupRequest
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.AgeGroupDetails
import com.ricardofrango.squadify.model.AgeGroupStaffMember
import com.ricardofrango.squadify.model.CreateAgeGroupsRequest

interface AgeGroupRepository {
    suspend fun getAgeGroups(clubId: String): List<AgeGroup>
    suspend fun createAgeGroups(request: CreateAgeGroupsRequest): List<AgeGroup>
    suspend fun updateAgeGroup(ageGroupId: String, request: com.ricardofrango.squadify.model.UpdateAgeGroupRequest)
    /** Deletes the age group for good, with its teams, games and training (and on the coach server its lone players). */
    suspend fun deleteAgeGroup(ageGroupId: String)
    suspend fun getAgeGroupDetails(ageGroupId: String): AgeGroupDetails
    suspend fun getAgeGroupSeasonRecord(ageGroupId: String): com.ricardofrango.squadify.model.AgeGroupSeasonRecord
    suspend fun getAgeGroupFullStats(ageGroupId: String): com.ricardofrango.squadify.model.FullStatsData
    suspend fun getAgeGroupStaff(ageGroupId: String): List<AgeGroupStaffMember>
    suspend fun addStaffToAgeGroup(ageGroupId: String, request: AddStaffToAgeGroupRequest)
    // The coach server only, for now (TECHNICAL_DECISIONS §72): the owner takes someone off the age group.
    suspend fun removeStaffFromAgeGroup(ageGroupId: String, userId: String)
}
