package com.ricardofrango.squadify.domain.service

import com.ricardofrango.squadify.model.LiveMatchUpdate
import kotlinx.coroutines.flow.Flow

interface LiveMatchBus {
    /**
     * Publishes an update to a specific match.
     */
    suspend fun publish(matchId: String, update: LiveMatchUpdate)

    /**
     * Returns a stream of updates for a specific match.
     */
    fun subscribe(matchId: String): Flow<LiveMatchUpdate>
}
