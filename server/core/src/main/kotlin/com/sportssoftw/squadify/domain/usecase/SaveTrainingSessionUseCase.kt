package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.access.AccessPolicy
import com.sportssoftw.squadify.domain.repository.AgeGroupRepository
import com.sportssoftw.squadify.domain.repository.TrainingSessionRepository
import com.sportssoftw.squadify.domain.service.TransactionManager
import com.sportssoftw.squadify.exception.NotFoundException
import com.sportssoftw.squadify.exception.ValidationException
import com.sportssoftw.squadify.model.SaveTrainingSessionRequest
import com.sportssoftw.squadify.model.TrainingSession
import kotlin.uuid.Uuid

/**
 * Add one session, or edit one (design 39e), behind `MANAGE_TRAINING`. The end must be after the start; the
 * date can be any day, as it isn't bound by the season's (association) dates. Adding (or moving) a session onto the date and start time of
 * another replaces that one, as bulk creation does.
 */
class SaveTrainingSessionUseCase(
    private val ageGroupRepository: AgeGroupRepository,
    private val trainingSessionRepository: TrainingSessionRepository,
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager
) {
    suspend fun add(userId: String, ageGroupId: String, request: SaveTrainingSessionRequest): TrainingSession = transactionManager.runInTransaction {
        val ageGroup = ageGroupRepository.getAgeGroupById(ageGroupId) ?: throw NotFoundException("Age group not found")
        accessPolicy.requireManageTraining(userId, ageGroup.scope, MANAGE_TRAINING_MESSAGE)
        accessPolicy.requireTrainingInPlan(ageGroup.clubId)
        validate(request)
        replaceClash(ageGroupId, request, keep = null)
        val session = TrainingSession(
            Uuid.random().toString(), ageGroupId, request.date, request.startTime, request.endTime,
            location = request.location.cleaned(), notes = request.notes.cleaned()
        )
        trainingSessionRepository.insertAll(listOf(session))
        session
    }

    suspend fun update(userId: String, sessionId: String, request: SaveTrainingSessionRequest): TrainingSession = transactionManager.runInTransaction {
        val stored = trainingSessionRepository.getById(sessionId) ?: throw NotFoundException("Training session not found")
        val ageGroup = ageGroupRepository.getAgeGroupById(stored.session.ageGroupId) ?: throw NotFoundException("Age group not found")
        accessPolicy.requireManageTraining(userId, ageGroup.scope, MANAGE_TRAINING_MESSAGE)
        accessPolicy.requireTrainingInPlan(ageGroup.clubId)
        validate(request)
        replaceClash(ageGroup.id, request, keep = sessionId)
        val updated = stored.session.copy(
            date = request.date, startTime = request.startTime, endTime = request.endTime,
            location = request.location.cleaned(), notes = request.notes.cleaned()
        )
        trainingSessionRepository.update(updated)
        updated
    }

    private fun validate(request: SaveTrainingSessionRequest) {
        if (request.endTime <= request.startTime) throw ValidationException("A session must end after it starts")
    }

    private suspend fun replaceClash(ageGroupId: String, request: SaveTrainingSessionRequest, keep: String?) {
        val clash = trainingSessionRepository.getByAgeGroup(ageGroupId).filter {
            it.session.id != keep && it.session.date == request.date && it.session.startTime == request.startTime
        }
        trainingSessionRepository.deleteAll(clash.map { it.session.id })
    }
}
