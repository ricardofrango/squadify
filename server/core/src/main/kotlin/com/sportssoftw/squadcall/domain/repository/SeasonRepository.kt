package com.sportssoftw.squadcall.domain.repository

import com.sportssoftw.squadcall.model.Season
import com.sportssoftw.squadcall.model.SeasonType
import kotlinx.datetime.LocalDate

interface SeasonRepository {
    suspend fun getSeasonsByClub(clubId: String): List<Season>
    suspend fun createSeason(season: Season): Season
    suspend fun getSeasonById(id: String): Season?
    suspend fun updateSeason(season: Season): Boolean
    suspend fun findByDetails(clubId: String, type: SeasonType, startDate: LocalDate, endDate: LocalDate): Season?
    suspend fun updateActiveStatus(seasonId: String, active: Boolean): Boolean
}
