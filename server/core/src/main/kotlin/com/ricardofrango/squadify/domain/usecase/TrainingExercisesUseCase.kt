package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.access.AccessPolicy
import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.TrainingExerciseRepository
import com.ricardofrango.squadify.domain.repository.TrainingSessionRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.SaveExerciseRequest
import com.ricardofrango.squadify.model.TrainingExercise
import kotlin.uuid.Uuid

/**
 * The exercise library of an age group and the exercises of a session (design 39f/h).
 * - The library is for the age group's staff (and coordinators), most used first.
 * - Creating and editing exercises, and setting a session's exercises, need `MANAGE_TRAINING`.
 * - An exercise needs a name; its duration, when given, is a positive number of minutes.
 * - A session takes only its own age group's exercises, each once, and not while it's cancelled.
 */
class TrainingExercisesUseCase(
    private val ageGroupRepository: AgeGroupRepository,
    private val trainingSessionRepository: TrainingSessionRepository,
    private val trainingExerciseRepository: TrainingExerciseRepository,
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager
) {
    suspend fun library(userId: String, ageGroupId: String): List<TrainingExercise> = transactionManager.runInTransaction {
        val ageGroup = ageGroup(ageGroupId)
        accessPolicy.requireRateTraining(userId, ageGroup.scope, RATE_TRAINING_MESSAGE)
        accessPolicy.requireTrainingInPlan(ageGroup.clubId)
        trainingExerciseRepository.getByAgeGroup(ageGroupId).sortedWith(compareByDescending<TrainingExercise> { it.usedCount }.thenBy { it.name.lowercase() })
    }

    suspend fun create(userId: String, ageGroupId: String, request: SaveExerciseRequest): TrainingExercise = transactionManager.runInTransaction {
        val ageGroup = ageGroup(ageGroupId)
        accessPolicy.requireManageTraining(userId, ageGroup.scope, MANAGE_TRAINING_MESSAGE)
        accessPolicy.requireTrainingInPlan(ageGroup.clubId)
        trainingExerciseRepository.create(validated(request).copy(id = Uuid.random().toString(), ageGroupId = ageGroupId))
    }

    suspend fun update(userId: String, exerciseId: String, request: SaveExerciseRequest): TrainingExercise = transactionManager.runInTransaction {
        val existing = trainingExerciseRepository.getById(exerciseId) ?: throw NotFoundException("Exercise not found")
        val ageGroup = ageGroup(existing.ageGroupId)
        accessPolicy.requireManageTraining(userId, ageGroup.scope, MANAGE_TRAINING_MESSAGE)
        accessPolicy.requireTrainingInPlan(ageGroup.clubId)
        val updated = validated(request).copy(id = existing.id, ageGroupId = existing.ageGroupId, usedCount = existing.usedCount)
        trainingExerciseRepository.update(updated)
        updated
    }

    suspend fun setForSession(userId: String, sessionId: String, exerciseIds: List<String>): List<TrainingExercise> = transactionManager.runInTransaction {
        val stored = trainingSessionRepository.getById(sessionId) ?: throw NotFoundException("Training session not found")
        val ageGroup = ageGroup(stored.session.ageGroupId)
        accessPolicy.requireManageTraining(userId, ageGroup.scope, MANAGE_TRAINING_MESSAGE)
        accessPolicy.requireTrainingInPlan(ageGroup.clubId)
        if (stored.session.cancelled) throw ValidationException("A cancelled session can't be changed")
        if (exerciseIds.toSet().size != exerciseIds.size) throw ValidationException("An exercise can be in a session only once")
        val library = trainingExerciseRepository.getByAgeGroup(ageGroup.id).map { it.id }.toSet()
        if (!library.containsAll(exerciseIds)) throw ValidationException("Exercises must come from the age group's library")
        trainingExerciseRepository.setForSession(sessionId, exerciseIds)
        trainingExerciseRepository.getForSession(sessionId)
    }

    private suspend fun ageGroup(id: String): AgeGroup = ageGroupRepository.getAgeGroupById(id) ?: throw NotFoundException("Age group not found")

    private fun validated(request: SaveExerciseRequest): TrainingExercise {
        val name = request.name.cleaned() ?: throw ValidationException("An exercise needs a name")
        val duration = request.durationMinutes
        if (duration != null && duration <= 0) throw ValidationException("The duration must be a positive number of minutes")
        return TrainingExercise(
            id = "", ageGroupId = "",
            name = name,
            description = request.description.cleaned(),
            durationMinutes = duration,
            objectives = request.objectives.cleaned()
        )
    }
}
