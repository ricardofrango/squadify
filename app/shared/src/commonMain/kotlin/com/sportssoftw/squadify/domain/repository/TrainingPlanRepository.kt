package com.sportssoftw.squadify.domain.repository

import com.sportssoftw.squadify.model.SaveTrainingPlanRequest
import com.sportssoftw.squadify.model.TrainingPlan
import com.sportssoftw.squadify.model.TrainingPlans

interface TrainingPlanRepository {
    suspend fun getTrainingPlans(ageGroupId: String): TrainingPlans
    suspend fun saveTrainingPlan(ageGroupId: String, request: SaveTrainingPlanRequest): TrainingPlan
}
