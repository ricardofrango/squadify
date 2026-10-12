package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.TrainingSessionRepository
import com.ricardofrango.squadify.model.AgeGroupTrainingStats
import com.ricardofrango.squadify.model.CreateTrainingSessionsRequest
import com.ricardofrango.squadify.model.PlayerTrainingStats
import com.ricardofrango.squadify.model.SaveExerciseRequest
import com.ricardofrango.squadify.model.TrainingExercise
import com.ricardofrango.squadify.model.TrainingRatingChange
import com.ricardofrango.squadify.model.TrainingRatings
import com.ricardofrango.squadify.model.SaveTrainingSessionRequest
import com.ricardofrango.squadify.model.TrainingSession
import com.ricardofrango.squadify.model.TrainingSessionDetails
import com.ricardofrango.squadify.model.TrainingSessions
import com.ricardofrango.squadify.model.TrainingSessionsPreview

class GetTrainingSessionsUseCase(private val repository: TrainingSessionRepository) {
    suspend operator fun invoke(ageGroupId: String): TrainingSessions = repository.getSessions(ageGroupId)
}

class GetTrainingSessionUseCase(private val repository: TrainingSessionRepository) {
    suspend operator fun invoke(sessionId: String): TrainingSessionDetails = repository.getSession(sessionId)
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

/** The exercise library and a session's exercises (design 39f/h). */
class TrainingExercisesUseCase(private val repository: TrainingSessionRepository) {
    suspend fun library(ageGroupId: String): List<TrainingExercise> = repository.getLibrary(ageGroupId)
    suspend fun create(ageGroupId: String, request: SaveExerciseRequest): TrainingExercise = repository.createExercise(ageGroupId, request)
    suspend fun update(exerciseId: String, request: SaveExerciseRequest): TrainingExercise = repository.updateExercise(exerciseId, request)
    suspend fun setForSession(sessionId: String, exerciseIds: List<String>): List<TrainingExercise> = repository.setSessionExercises(sessionId, exerciseIds)
}

/** Rating players after a session (design 39g), staff only. */
class TrainingRatingsUseCase(private val repository: TrainingSessionRepository) {
    suspend fun get(sessionId: String): TrainingRatings = repository.getRatings(sessionId)
    suspend fun save(sessionId: String, changes: List<TrainingRatingChange>): TrainingRatings = repository.saveRatings(sessionId, changes)
}

/** Training attendance and ratings (design 39j), staff only. */
class TrainingStatsUseCase(private val repository: TrainingSessionRepository) {
    suspend fun ageGroup(ageGroupId: String): AgeGroupTrainingStats = repository.getAgeGroupTrainingStats(ageGroupId)
    suspend fun player(playerId: String): PlayerTrainingStats = repository.getPlayerTrainingStats(playerId)
}

class ChangeTrainingSessionUseCase(private val repository: TrainingSessionRepository) {
    suspend fun cancel(sessionId: String) = repository.cancelSession(sessionId)
    suspend fun restore(sessionId: String) = repository.restoreSession(sessionId)
    suspend fun delete(sessionId: String) = repository.deleteSession(sessionId)
}
