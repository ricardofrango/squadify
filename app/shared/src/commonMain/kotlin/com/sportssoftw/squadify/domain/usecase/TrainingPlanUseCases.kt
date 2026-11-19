package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.TrainingPlanRepository
import com.sportssoftw.squadify.model.SaveTrainingPlanRequest
import com.sportssoftw.squadify.model.TrainingPlan
import com.sportssoftw.squadify.model.TrainingPlans

class GetTrainingPlansUseCase(private val repository: TrainingPlanRepository) {
    suspend operator fun invoke(ageGroupId: String): TrainingPlans = repository.getTrainingPlans(ageGroupId)
}

class SaveTrainingPlanUseCase(private val repository: TrainingPlanRepository) {
    suspend operator fun invoke(ageGroupId: String, request: SaveTrainingPlanRequest): TrainingPlan =
        repository.saveTrainingPlan(ageGroupId, request)
}
