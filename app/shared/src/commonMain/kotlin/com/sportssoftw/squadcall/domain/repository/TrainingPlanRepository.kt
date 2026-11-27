package com.sportssoftw.squadcall.domain.repository

import com.sportssoftw.squadcall.model.SaveTrainingPlanRequest
import com.sportssoftw.squadcall.model.TrainingPlan
import com.sportssoftw.squadcall.model.TrainingPlans

interface TrainingPlanRepository {
    suspend fun getTrainingPlans(ageGroupId: String): TrainingPlans
    suspend fun saveTrainingPlan(ageGroupId: String, request: SaveTrainingPlanRequest): TrainingPlan
}
