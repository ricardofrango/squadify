package com.ricardofrango.clubmanager.domain.repository

import com.ricardofrango.clubmanager.model.Season
import com.ricardofrango.clubmanager.model.SeasonType
import kotlinx.datetime.LocalDate

interface SeasonRepository {
    suspend fun getSeasonsByClub(clubId: String): List<Season>
    suspend fun createSeason(season: Season): Season
    suspend fun getSeasonById(id: String): Season?
    suspend fun updateSeason(season: Season): Boolean
    suspend fun findByDetails(clubId: String, type: SeasonType, startDate: LocalDate, endDate: LocalDate): Season?
    suspend fun updateActiveStatus(seasonId: String, active: Boolean): Boolean
    suspend fun getActiveSeasonsByClub(clubId: String): List<Season>
}
