package com.ricardofrango.clubmanager.domain.repository

import com.ricardofrango.clubmanager.model.AgeGroupPlayer
import com.ricardofrango.clubmanager.model.Player

interface AgeGroupPlayerRepository {
    suspend fun getAgeGroupPlayersByClub(clubId: String): List<AgeGroupPlayer>

    /** Club players who may be added to [ageGroupId]: old enough for it and not already in it. */
    suspend fun getEligiblePlayers(ageGroupId: String): List<Player>

    suspend fun addPlayers(ageGroupId: String, playerIds: List<String>): List<AgeGroupPlayer>
}
