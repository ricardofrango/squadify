package com.ricardofrango.squadify.domain.repository

import com.ricardofrango.squadify.model.TrainingSession
import kotlin.time.Instant

/** A stored session: [cancelledBy] is the user id; names and counts are filled in by the use cases. */
interface TrainingSessionRepository {
    suspend fun getByAgeGroup(ageGroupId: String): List<StoredTrainingSession>
    suspend fun getById(id: String): StoredTrainingSession?
    suspend fun insertAll(sessions: List<TrainingSession>)
    suspend fun update(session: TrainingSession): Boolean
    suspend fun setCancelled(id: String, at: Instant?, by: String?): Boolean
    // Deletes the sessions and everything attached to them (exercises, ratings).
    suspend fun deleteAll(ids: List<String>)
    // Per session: how many exercises and ratings it has, for the replace warnings and the list rows.
    suspend fun contentCounts(ids: List<String>): Map<String, SessionContentCounts>
}

data class StoredTrainingSession(val session: TrainingSession, val cancelledBy: String?)

data class SessionContentCounts(val exercises: Int, val ratings: Int)
