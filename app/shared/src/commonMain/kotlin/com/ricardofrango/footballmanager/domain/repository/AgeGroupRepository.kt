package com.ricardofrango.footballmanager.domain.repository

import com.ricardofrango.footballmanager.model.AgeGroup
import com.ricardofrango.footballmanager.model.AgeGroupDetails
import com.ricardofrango.footballmanager.model.CreateAgeGroupsRequest

interface AgeGroupRepository {
    suspend fun getAgeGroups(clubId: String): List<AgeGroup>
    suspend fun createAgeGroups(request: CreateAgeGroupsRequest): List<AgeGroup>
    suspend fun getAgeGroupDetails(ageGroupId: String): AgeGroupDetails
}
