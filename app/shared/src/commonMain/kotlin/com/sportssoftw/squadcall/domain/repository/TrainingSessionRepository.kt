package com.sportssoftw.squadcall.domain.repository

import com.sportssoftw.squadcall.model.AgeGroupTrainingStats
import com.sportssoftw.squadcall.model.CreateTrainingSessionsRequest
import com.sportssoftw.squadcall.model.PlayerTrainingStats
import com.sportssoftw.squadcall.model.SaveExerciseRequest
import com.sportssoftw.squadcall.model.TrainingExercise
import com.sportssoftw.squadcall.model.TrainingRatingChange
import com.sportssoftw.squadcall.model.TrainingRatings
import com.sportssoftw.squadcall.model.SaveTrainingSessionRequest
import com.sportssoftw.squadcall.model.TrainingSession
import com.sportssoftw.squadcall.model.TrainingSessionDetails
import com.sportssoftw.squadcall.model.TrainingSessions
import com.sportssoftw.squadcall.model.TrainingSessionsPreview

interface TrainingSessionRepository {
    suspend fun getSessions(ageGroupId: String): TrainingSessions
    suspend fun getSession(sessionId: String): TrainingSessionDetails
    suspend fun preview(ageGroupId: String, request: CreateTrainingSessionsRequest): TrainingSessionsPreview
    suspend fun createInBulk(ageGroupId: String, request: CreateTrainingSessionsRequest): Int
    suspend fun addSession(ageGroupId: String, request: SaveTrainingSessionRequest): TrainingSession
    suspend fun updateSession(sessionId: String, request: SaveTrainingSessionRequest): TrainingSession
    suspend fun cancelSession(sessionId: String)
    suspend fun restoreSession(sessionId: String)
    suspend fun deleteSession(sessionId: String)
    suspend fun getLibrary(ageGroupId: String): List<TrainingExercise>
    suspend fun createExercise(ageGroupId: String, request: SaveExerciseRequest): TrainingExercise
    suspend fun updateExercise(exerciseId: String, request: SaveExerciseRequest): TrainingExercise
    suspend fun setSessionExercises(sessionId: String, exerciseIds: List<String>): List<TrainingExercise>
    suspend fun getRatings(sessionId: String): TrainingRatings
    suspend fun getAgeGroupTrainingStats(ageGroupId: String): AgeGroupTrainingStats
    suspend fun getPlayerTrainingStats(playerId: String): PlayerTrainingStats
    suspend fun saveRatings(sessionId: String, changes: List<TrainingRatingChange>): TrainingRatings
}
