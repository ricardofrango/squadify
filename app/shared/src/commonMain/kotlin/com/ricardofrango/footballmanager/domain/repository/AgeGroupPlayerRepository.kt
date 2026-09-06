package com.ricardofrango.footballmanager.domain.repository

import com.ricardofrango.footballmanager.model.AgeGroupPlayer

interface AgeGroupPlayerRepository {
    suspend fun getAgeGroupPlayersByClub(clubId: String): List<AgeGroupPlayer>
}
