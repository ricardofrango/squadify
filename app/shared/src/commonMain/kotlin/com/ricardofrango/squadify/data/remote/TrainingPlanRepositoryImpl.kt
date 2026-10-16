package com.ricardofrango.squadify.data.remote

import com.ricardofrango.squadify.domain.config.AppConfig
import com.ricardofrango.squadify.domain.repository.TrainingPlanRepository
import com.ricardofrango.squadify.model.SaveTrainingPlanRequest
import com.ricardofrango.squadify.model.TrainingPlan
import com.ricardofrango.squadify.model.TrainingPlans
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

class TrainingPlanRepositoryImpl(
    private val httpClient: HttpClient,
    private val appConfig: AppConfig
) : TrainingPlanRepository {

    override suspend fun getTrainingPlans(ageGroupId: String): TrainingPlans =
        httpClient.get("${appConfig.baseUrl}/age-groups/$ageGroupId/training-plans").handleResponse()

    override suspend fun saveTrainingPlan(ageGroupId: String, request: SaveTrainingPlanRequest): TrainingPlan =
        httpClient.put("${appConfig.baseUrl}/age-groups/$ageGroupId/training-plans") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.handleResponse()
}
