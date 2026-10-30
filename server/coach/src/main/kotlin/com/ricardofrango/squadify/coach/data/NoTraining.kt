package com.ricardofrango.squadify.coach.data

import com.ricardofrango.squadify.domain.repository.SessionContentCounts
import com.ricardofrango.squadify.domain.repository.StoredTrainingSession
import com.ricardofrango.squadify.domain.repository.TrainingRatingRepository
import com.ricardofrango.squadify.domain.repository.TrainingSessionRepository
import com.ricardofrango.squadify.model.TrainingRating
import com.ricardofrango.squadify.model.TrainingSession
import kotlin.time.Instant

// The coach product has no training (REQUIREMENTS §5c) and its database no training tables. Full
// stats still reads trainings, so here there are none; nothing on the coach server ever writes one.

object NoTrainingSessions : TrainingSessionRepository {
    override suspend fun getByAgeGroup(ageGroupId: String): List<StoredTrainingSession> = emptyList()
    override suspend fun getById(id: String): StoredTrainingSession? = null
    override suspend fun insertAll(sessions: List<TrainingSession>) = unsupported()
    override suspend fun update(session: TrainingSession): Boolean = unsupported()
    override suspend fun setCancelled(id: String, at: Instant?, by: String?): Boolean = unsupported()
    override suspend fun deleteAll(ids: List<String>) = unsupported()
    override suspend fun contentCounts(ids: List<String>): Map<String, SessionContentCounts> = emptyMap()
}

object NoTrainingRatings : TrainingRatingRepository {
    override suspend fun getForSession(sessionId: String): List<TrainingRating> = emptyList()
    override suspend fun getForSessions(sessionIds: List<String>): Map<String, List<TrainingRating>> = emptyMap()
    override suspend fun save(sessionId: String, changes: Map<String, Int?>, updatedBy: String) = unsupported()
}

private fun unsupported(): Nothing = throw UnsupportedOperationException("The coach server has no training")
