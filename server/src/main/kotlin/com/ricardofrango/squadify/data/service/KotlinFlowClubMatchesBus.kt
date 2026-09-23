package com.ricardofrango.squadify.data.service

import com.ricardofrango.squadify.domain.service.ClubMatchesBus
import com.ricardofrango.squadify.model.ClubMatchesUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap

class KotlinFlowClubMatchesBus : ClubMatchesBus {
    // Map of clubId to a SharedFlow that emits updates
    private val clubFlows = ConcurrentHashMap<String, MutableSharedFlow<ClubMatchesUpdate>>()

    override suspend fun publish(clubId: String, update: ClubMatchesUpdate) {
        getOrCreateFlow(clubId).emit(update)
    }

    override fun subscribe(clubId: String): Flow<ClubMatchesUpdate> {
        return getOrCreateFlow(clubId).asSharedFlow()
    }

    private fun getOrCreateFlow(clubId: String): MutableSharedFlow<ClubMatchesUpdate> {
        return clubFlows.getOrPut(clubId) {
            MutableSharedFlow(extraBufferCapacity = 64)
        }
    }
}
