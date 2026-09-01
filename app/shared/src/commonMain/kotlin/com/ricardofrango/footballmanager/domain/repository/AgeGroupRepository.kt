package com.ricardofrango.footballmanager.domain.repository

import com.ricardofrango.footballmanager.model.AgeGroup

interface AgeGroupRepository {
    suspend fun getAgeGroups(clubId: String): List<AgeGroup>
}
