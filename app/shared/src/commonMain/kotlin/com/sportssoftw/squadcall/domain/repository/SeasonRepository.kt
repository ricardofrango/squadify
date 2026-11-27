package com.sportssoftw.squadcall.domain.repository

import com.sportssoftw.squadcall.model.CreateSeasonRequest
import com.sportssoftw.squadcall.model.Season

interface SeasonRepository {
    suspend fun getSeasons(clubId: String): List<Season>
    suspend fun createSeason(request: CreateSeasonRequest): Season
}
