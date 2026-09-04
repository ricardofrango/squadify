package com.ricardofrango.footballmanager.domain.repository

import com.ricardofrango.footballmanager.model.CreateSeasonRequest
import com.ricardofrango.footballmanager.model.Season

interface SeasonRepository {
    suspend fun getSeasons(clubId: String): List<Season>
    suspend fun createSeason(request: CreateSeasonRequest): Season
}
