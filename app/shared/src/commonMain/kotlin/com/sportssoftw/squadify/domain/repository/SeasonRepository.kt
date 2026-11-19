package com.sportssoftw.squadify.domain.repository

import com.sportssoftw.squadify.model.CreateSeasonRequest
import com.sportssoftw.squadify.model.Season

interface SeasonRepository {
    suspend fun getSeasons(clubId: String): List<Season>
    suspend fun createSeason(request: CreateSeasonRequest): Season
}
