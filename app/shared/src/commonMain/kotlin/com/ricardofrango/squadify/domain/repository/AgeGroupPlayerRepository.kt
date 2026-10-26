package com.ricardofrango.squadify.domain.repository

import com.ricardofrango.squadify.model.AgeGroupPlayer
import com.ricardofrango.squadify.model.Player

interface AgeGroupPlayerRepository {
    /** Club players who may be added to [ageGroupId]: old enough for it and not already in it. */
    suspend fun getEligiblePlayers(ageGroupId: String): List<Player>

    suspend fun addPlayers(ageGroupId: String, playerIds: List<String>): List<AgeGroupPlayer>

    /** Out of [ageGroupId]'s pool, not out of the club. */
    suspend fun removePlayer(ageGroupId: String, playerId: String)
}
