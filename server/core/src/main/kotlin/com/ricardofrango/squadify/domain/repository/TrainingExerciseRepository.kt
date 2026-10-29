package com.ricardofrango.squadify.domain.repository

import com.ricardofrango.squadify.model.TrainingExercise

interface TrainingExerciseRepository {
    // With usedCount.
    suspend fun getByAgeGroup(ageGroupId: String): List<TrainingExercise>
    suspend fun getById(id: String): TrainingExercise?
    suspend fun create(exercise: TrainingExercise): TrainingExercise
    suspend fun update(exercise: TrainingExercise): Boolean
    // A session's exercises, in order.
    suspend fun getForSession(sessionId: String): List<TrainingExercise>
    suspend fun setForSession(sessionId: String, exerciseIds: List<String>)
}
