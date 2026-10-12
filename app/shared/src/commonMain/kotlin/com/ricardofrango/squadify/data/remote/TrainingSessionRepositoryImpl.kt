package com.ricardofrango.squadify.data.remote

import com.ricardofrango.squadify.domain.config.AppConfig
import com.ricardofrango.squadify.domain.repository.TrainingSessionRepository
import com.ricardofrango.squadify.model.CreateTrainingSessionsRequest
import com.ricardofrango.squadify.model.SaveTrainingSessionRequest
import com.ricardofrango.squadify.model.TrainingSession
import com.ricardofrango.squadify.model.TrainingSessions
import com.ricardofrango.squadify.model.TrainingSessionsPreview
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
        httpClient.get(ageGroupUrl(ageGroupId)).handleResponse()

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
}
