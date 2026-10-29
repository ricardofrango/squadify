package com.ricardofrango.squadify.domain.repository

import com.ricardofrango.squadify.model.TrainingRating

interface TrainingRatingRepository {
    suspend fun getForSession(sessionId: String): List<TrainingRating>
    // Every rating of these sessions at once, by session id (lists, stats, Home).
    suspend fun getForSessions(sessionIds: List<String>): Map<String, List<TrainingRating>>
    // Upserts the ratings with a value and deletes those with null.
    suspend fun save(sessionId: String, changes: Map<String, Int?>, updatedBy: String)
}
