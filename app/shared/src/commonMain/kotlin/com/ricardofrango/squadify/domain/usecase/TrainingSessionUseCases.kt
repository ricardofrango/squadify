package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.TrainingSessionRepository
import com.ricardofrango.squadify.model.CreateTrainingSessionsRequest
import com.ricardofrango.squadify.model.SaveTrainingSessionRequest
import com.ricardofrango.squadify.model.TrainingSession
import com.ricardofrango.squadify.model.TrainingSessions
import com.ricardofrango.squadify.model.TrainingSessionsPreview

class GetTrainingSessionsUseCase(private val repository: TrainingSessionRepository) {
    suspend operator fun invoke(ageGroupId: String): TrainingSessions = repository.getSessions(ageGroupId)
}

/** Bulk creation: [preview] changes nothing, [create] returns how many sessions were created. */
class CreateTrainingSessionsUseCase(private val repository: TrainingSessionRepository) {
    suspend fun preview(ageGroupId: String, request: CreateTrainingSessionsRequest): TrainingSessionsPreview = repository.preview(ageGroupId, request)
    suspend fun create(ageGroupId: String, request: CreateTrainingSessionsRequest): Int = repository.createInBulk(ageGroupId, request)
}

class SaveTrainingSessionUseCase(private val repository: TrainingSessionRepository) {
    suspend fun add(ageGroupId: String, request: SaveTrainingSessionRequest): TrainingSession = repository.addSession(ageGroupId, request)
    suspend fun update(sessionId: String, request: SaveTrainingSessionRequest): TrainingSession = repository.updateSession(sessionId, request)
}

class ChangeTrainingSessionUseCase(private val repository: TrainingSessionRepository) {
    suspend fun cancel(sessionId: String) = repository.cancelSession(sessionId)
    suspend fun restore(sessionId: String) = repository.restoreSession(sessionId)
    suspend fun delete(sessionId: String) = repository.deleteSession(sessionId)
}
