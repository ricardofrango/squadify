package com.ricardofrango.footballmanager.domain.repository

import com.ricardofrango.footballmanager.model.Season

interface SeasonRepository {
    suspend fun getSeasons(clubId: String): List<Season>
}
