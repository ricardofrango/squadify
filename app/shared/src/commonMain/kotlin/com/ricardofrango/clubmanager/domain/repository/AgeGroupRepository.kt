package com.ricardofrango.clubmanager.domain.repository

import com.ricardofrango.clubmanager.model.AddStaffToAgeGroupRequest
import com.ricardofrango.clubmanager.model.AgeGroup
import com.ricardofrango.clubmanager.model.AgeGroupDetails
import com.ricardofrango.clubmanager.model.AgeGroupStaffMember
import com.ricardofrango.clubmanager.model.CreateAgeGroupsRequest

interface AgeGroupRepository {
    suspend fun getAgeGroups(clubId: String): List<AgeGroup>
    suspend fun createAgeGroups(request: CreateAgeGroupsRequest): List<AgeGroup>
    suspend fun getAgeGroupDetails(ageGroupId: String): AgeGroupDetails
    suspend fun getAgeGroupStaff(ageGroupId: String): List<AgeGroupStaffMember>
    suspend fun addStaffToAgeGroup(ageGroupId: String, request: AddStaffToAgeGroupRequest)
}
