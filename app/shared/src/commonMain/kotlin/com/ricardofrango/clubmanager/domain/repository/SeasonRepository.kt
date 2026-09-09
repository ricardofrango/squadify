package com.ricardofrango.clubmanager.domain.repository

import com.ricardofrango.clubmanager.model.CreateSeasonRequest
import com.ricardofrango.clubmanager.model.Season

interface SeasonRepository {
    suspend fun getSeasons(clubId: String): List<Season>
    suspend fun createSeason(request: CreateSeasonRequest): Season
}
