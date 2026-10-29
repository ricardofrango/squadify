package com.ricardofrango.squadify.data.service

import com.ricardofrango.squadify.domain.service.LiveMatchBus
import com.ricardofrango.squadify.model.LiveMatchUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap

class KotlinFlowMatchBus : LiveMatchBus {
    // Map of matchId to a SharedFlow that emits updates
    private val matchFlows = ConcurrentHashMap<String, MutableSharedFlow<LiveMatchUpdate>>()

    override suspend fun publish(matchId: String, update: LiveMatchUpdate) {
        getOrCreateFlow(matchId).emit(update)
    }

    override fun subscribe(matchId: String): Flow<LiveMatchUpdate> {
        return getOrCreateFlow(matchId).asSharedFlow()
    }

    private fun getOrCreateFlow(matchId: String): MutableSharedFlow<LiveMatchUpdate> {
        return matchFlows.getOrPut(matchId) {
            MutableSharedFlow(extraBufferCapacity = 64)
        }
    }
}
