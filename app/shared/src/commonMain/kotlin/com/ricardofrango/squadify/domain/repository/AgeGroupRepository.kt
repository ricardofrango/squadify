package com.ricardofrango.squadify.domain.repository

import com.ricardofrango.squadify.model.AddStaffToAgeGroupRequest
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.AgeGroupDetails
import com.ricardofrango.squadify.model.AgeGroupStaffMember
import com.ricardofrango.squadify.model.CreateAgeGroupsRequest

interface AgeGroupRepository {
    suspend fun getAgeGroups(clubId: String): List<AgeGroup>
    suspend fun createAgeGroups(request: CreateAgeGroupsRequest): List<AgeGroup>
    suspend fun getAgeGroupDetails(ageGroupId: String): AgeGroupDetails
    suspend fun getAgeGroupSeasonRecord(ageGroupId: String): com.ricardofrango.squadify.model.AgeGroupSeasonRecord
    suspend fun getAgeGroupStaff(ageGroupId: String): List<AgeGroupStaffMember>
    suspend fun addStaffToAgeGroup(ageGroupId: String, request: AddStaffToAgeGroupRequest)
}
