package com.ricardofrango.squadify.domain.repository

import com.ricardofrango.squadify.model.CreateSeasonRequest
import com.ricardofrango.squadify.model.Season

interface SeasonRepository {
    suspend fun getSeasons(clubId: String): List<Season>
    suspend fun createSeason(request: CreateSeasonRequest): Season
}
