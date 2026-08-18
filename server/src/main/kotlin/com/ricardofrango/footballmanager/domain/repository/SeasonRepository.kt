package com.ricardofrango.footballmanager.domain.repository

import com.ricardofrango.footballmanager.model.Season

interface SeasonRepository {
    suspend fun getSeasonsByClub(clubId: String): List<Season>
    suspend fun createSeason(season: Season): Boolean
    suspend fun getSeasonById(id: String): Season?
}
