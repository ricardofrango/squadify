package com.sportssoftw.squadcall.data.remote

import com.sportssoftw.squadcall.data.cache.cachedGet
import com.sportssoftw.squadcall.domain.config.AppConfig
import com.sportssoftw.squadcall.domain.repository.TrainingPlanRepository
import com.sportssoftw.squadcall.model.SaveTrainingPlanRequest
import com.sportssoftw.squadcall.model.TrainingPlan
import com.sportssoftw.squadcall.model.TrainingPlans
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

class TrainingPlanRepositoryImpl(
    private val httpClient: HttpClient,
    private val appConfig: AppConfig
) : TrainingPlanRepository {

    override suspend fun getTrainingPlans(ageGroupId: String): TrainingPlans =
        httpClient.cachedGet("${appConfig.baseUrl}/age-groups/$ageGroupId/training-plans")

    override suspend fun saveTrainingPlan(ageGroupId: String, request: SaveTrainingPlanRequest): TrainingPlan =
        httpClient.put("${appConfig.baseUrl}/age-groups/$ageGroupId/training-plans") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.handleResponse()
}
