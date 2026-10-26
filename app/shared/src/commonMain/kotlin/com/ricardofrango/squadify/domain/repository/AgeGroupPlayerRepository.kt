package com.ricardofrango.squadify.domain.repository

import com.ricardofrango.squadify.model.AgeGroupPlayer
import com.ricardofrango.squadify.model.PlayersPage

interface AgeGroupPlayerRepository {
    /** Club players who may be added to [ageGroupId]: old enough for it and not already in it. */
    /** One page of the club players who may join [ageGroupId]'s pool, by name, searched on [query]. */
    suspend fun getEligiblePlayersPage(ageGroupId: String, query: String, offset: Int, limit: Int): PlayersPage

    suspend fun addPlayers(ageGroupId: String, playerIds: List<String>): List<AgeGroupPlayer>

    /** Out of [ageGroupId]'s pool, not out of the club. */
    suspend fun removePlayer(ageGroupId: String, playerId: String)
}
