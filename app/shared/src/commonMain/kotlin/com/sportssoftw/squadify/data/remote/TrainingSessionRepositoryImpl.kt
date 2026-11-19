package com.sportssoftw.squadify.data.remote

import com.sportssoftw.squadify.data.cache.CacheKind
import com.sportssoftw.squadify.data.cache.cachedGet
import com.sportssoftw.squadify.domain.config.AppConfig
import com.sportssoftw.squadify.domain.repository.TrainingSessionRepository
import com.sportssoftw.squadify.model.AgeGroupTrainingStats
import com.sportssoftw.squadify.model.CreateTrainingSessionsRequest
import com.sportssoftw.squadify.model.PlayerTrainingStats
import com.sportssoftw.squadify.model.SaveExerciseRequest
import com.sportssoftw.squadify.model.SetSessionExercisesRequest
import com.sportssoftw.squadify.model.SaveTrainingRatingsRequest
import com.sportssoftw.squadify.model.TrainingExercise
import com.sportssoftw.squadify.model.TrainingRatingChange
import com.sportssoftw.squadify.model.TrainingRatings
import com.sportssoftw.squadify.model.SaveTrainingSessionRequest
import com.sportssoftw.squadify.model.TrainingSession
import com.sportssoftw.squadify.model.TrainingSessionDetails
import com.sportssoftw.squadify.model.TrainingSessions
import com.sportssoftw.squadify.model.TrainingSessionsPreview
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

class TrainingSessionRepositoryImpl(
    private val httpClient: HttpClient,
    private val appConfig: AppConfig
) : TrainingSessionRepository {

    private fun ageGroupUrl(ageGroupId: String) = "${appConfig.baseUrl}/age-groups/$ageGroupId/training-sessions"
    private fun sessionUrl(sessionId: String) = "${appConfig.baseUrl}/training-sessions/$sessionId"

    override suspend fun getSessions(ageGroupId: String): TrainingSessions =
        httpClient.cachedGet(ageGroupUrl(ageGroupId))

    override suspend fun getSession(sessionId: String): TrainingSessionDetails =
        httpClient.cachedGet(sessionUrl(sessionId))

    override suspend fun preview(ageGroupId: String, request: CreateTrainingSessionsRequest): TrainingSessionsPreview =
        httpClient.post("${ageGroupUrl(ageGroupId)}/preview") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.handleResponse()

    override suspend fun createInBulk(ageGroupId: String, request: CreateTrainingSessionsRequest): Int =
        httpClient.post("${ageGroupUrl(ageGroupId)}/bulk") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.handleResponse()

    override suspend fun addSession(ageGroupId: String, request: SaveTrainingSessionRequest): TrainingSession =
        httpClient.post(ageGroupUrl(ageGroupId)) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.handleResponse()

    override suspend fun updateSession(sessionId: String, request: SaveTrainingSessionRequest): TrainingSession =
        httpClient.patch(sessionUrl(sessionId)) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.handleResponse()

    override suspend fun cancelSession(sessionId: String) =
        httpClient.post("${sessionUrl(sessionId)}/cancel").handleResponse<Unit>()

    override suspend fun restoreSession(sessionId: String) =
        httpClient.post("${sessionUrl(sessionId)}/restore").handleResponse<Unit>()

    override suspend fun deleteSession(sessionId: String) =
        httpClient.delete(sessionUrl(sessionId)).handleResponse<Unit>()

    override suspend fun getLibrary(ageGroupId: String): List<TrainingExercise> =
        httpClient.cachedGet("${appConfig.baseUrl}/age-groups/$ageGroupId/exercises")

    override suspend fun createExercise(ageGroupId: String, request: SaveExerciseRequest): TrainingExercise =
        httpClient.post("${appConfig.baseUrl}/age-groups/$ageGroupId/exercises") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.handleResponse()

    override suspend fun updateExercise(exerciseId: String, request: SaveExerciseRequest): TrainingExercise =
        httpClient.patch("${appConfig.baseUrl}/exercises/$exerciseId") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.handleResponse()

    override suspend fun getAgeGroupTrainingStats(ageGroupId: String): AgeGroupTrainingStats =
        httpClient.cachedGet("${appConfig.baseUrl}/age-groups/$ageGroupId/training-stats")

    override suspend fun getPlayerTrainingStats(playerId: String): PlayerTrainingStats =
        httpClient.cachedGet("${appConfig.baseUrl}/players/$playerId/training-stats")

    override suspend fun getRatings(sessionId: String): TrainingRatings =
        httpClient.cachedGet("${sessionUrl(sessionId)}/ratings", CacheKind.MATCH)

    override suspend fun saveRatings(sessionId: String, changes: List<TrainingRatingChange>): TrainingRatings =
        httpClient.put("${sessionUrl(sessionId)}/ratings") {
            contentType(ContentType.Application.Json)
            setBody(SaveTrainingRatingsRequest(changes))
        }.handleResponse()

    override suspend fun setSessionExercises(sessionId: String, exerciseIds: List<String>): List<TrainingExercise> =
        httpClient.put("${sessionUrl(sessionId)}/exercises") {
            contentType(ContentType.Application.Json)
            setBody(SetSessionExercisesRequest(exerciseIds))
        }.handleResponse()
}
