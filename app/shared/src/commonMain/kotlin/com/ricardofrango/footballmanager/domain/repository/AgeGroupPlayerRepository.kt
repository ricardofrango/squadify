package com.ricardofrango.footballmanager.domain.repository

import com.ricardofrango.footballmanager.model.AgeGroupPlayer
import com.ricardofrango.footballmanager.model.Player

interface AgeGroupPlayerRepository {
    suspend fun getAgeGroupPlayersByClub(clubId: String): List<AgeGroupPlayer>

    /** Club players who may be added to [ageGroupId]: old enough for it and not already in it. */
    suspend fun getEligiblePlayers(ageGroupId: String): List<Player>

    suspend fun addPlayers(ageGroupId: String, playerIds: List<String>): List<AgeGroupPlayer>
}
