package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.TrainingPlanRepository
import com.sportssoftw.squadcall.model.SaveTrainingPlanRequest
import com.sportssoftw.squadcall.model.TrainingPlan
import com.sportssoftw.squadcall.model.TrainingPlans

class GetTrainingPlansUseCase(private val repository: TrainingPlanRepository) {
    suspend operator fun invoke(ageGroupId: String): TrainingPlans = repository.getTrainingPlans(ageGroupId)
}

class SaveTrainingPlanUseCase(private val repository: TrainingPlanRepository) {
    suspend operator fun invoke(ageGroupId: String, request: SaveTrainingPlanRequest): TrainingPlan =
        repository.saveTrainingPlan(ageGroupId, request)
}
