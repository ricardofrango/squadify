package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.access.AccessPolicy
import com.sportssoftw.squadcall.domain.repository.AgeGroupRepository
import com.sportssoftw.squadcall.domain.repository.TrainingSessionRepository
import com.sportssoftw.squadcall.domain.service.TransactionManager
import com.sportssoftw.squadcall.exception.NotFoundException
import com.sportssoftw.squadcall.model.AgeGroup
import com.sportssoftw.squadcall.domain.repository.StoredTrainingSession
import kotlin.time.Clock

/**
 * Cancel, restore or delete one session (design 39f), behind `MANAGE_TRAINING`. Cancelling keeps the session
 * and its content on the calendar (and records who and when); deleting removes it with its exercises and
 * ratings.
 */
class ChangeTrainingSessionUseCase(
    private val ageGroupRepository: AgeGroupRepository,
    private val trainingSessionRepository: TrainingSessionRepository,
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager
) {
    suspend fun cancel(userId: String, sessionId: String) = transactionManager.runInTransaction {
        authorized(userId, sessionId)
        trainingSessionRepository.setCancelled(sessionId, Clock.System.now(), userId)
        Unit
    }

    suspend fun restore(userId: String, sessionId: String) = transactionManager.runInTransaction {
        authorized(userId, sessionId)
        trainingSessionRepository.setCancelled(sessionId, null, null)
        Unit
    }

    suspend fun delete(userId: String, sessionId: String) = transactionManager.runInTransaction {
        authorized(userId, sessionId)
        trainingSessionRepository.deleteAll(listOf(sessionId))
    }

    private suspend fun authorized(userId: String, sessionId: String): Pair<StoredTrainingSession, AgeGroup> {
        val stored = trainingSessionRepository.getById(sessionId) ?: throw NotFoundException("Training session not found")
        val ageGroup = ageGroupRepository.getAgeGroupById(stored.session.ageGroupId) ?: throw NotFoundException("Age group not found")
        accessPolicy.requireManageTraining(userId, ageGroup.scope, MANAGE_TRAINING_MESSAGE)
        accessPolicy.requireTrainingInPlan(ageGroup.clubId)
        return stored to ageGroup
    }
}
