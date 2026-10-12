package com.ricardofrango.squadify.domain.repository

import com.ricardofrango.squadify.model.CreateTrainingSessionsRequest
import com.ricardofrango.squadify.model.SaveTrainingSessionRequest
import com.ricardofrango.squadify.model.TrainingSession
import com.ricardofrango.squadify.model.TrainingSessionDetails
import com.ricardofrango.squadify.model.TrainingSessions
import com.ricardofrango.squadify.model.TrainingSessionsPreview

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
}
