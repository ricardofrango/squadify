package com.ricardofrango.squadify.domain.service

import com.ricardofrango.squadify.model.ClubMatchesUpdate
import kotlinx.coroutines.flow.Flow

interface ClubMatchesBus {
    /**
     * Publishes an update to a specific club's matches list.
     */
    suspend fun publish(clubId: String, update: ClubMatchesUpdate)

    /**
     * Returns a stream of matches-list updates for a specific club.
     */
    fun subscribe(clubId: String): Flow<ClubMatchesUpdate>
}
