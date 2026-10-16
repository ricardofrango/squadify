package com.ricardofrango.squadify.domain.repository

import com.ricardofrango.squadify.model.SaveTrainingPlanRequest
import com.ricardofrango.squadify.model.TrainingPlan
import com.ricardofrango.squadify.model.TrainingPlans

interface TrainingPlanRepository {
    suspend fun getTrainingPlans(ageGroupId: String): TrainingPlans
    suspend fun saveTrainingPlan(ageGroupId: String, request: SaveTrainingPlanRequest): TrainingPlan
}
