package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.TrainingPlanRepository
import com.ricardofrango.squadify.model.SaveTrainingPlanRequest
import com.ricardofrango.squadify.model.TrainingPlan
import com.ricardofrango.squadify.model.TrainingPlans

class GetTrainingPlansUseCase(private val repository: TrainingPlanRepository) {
    suspend operator fun invoke(ageGroupId: String): TrainingPlans = repository.getTrainingPlans(ageGroupId)
}

class SaveTrainingPlanUseCase(private val repository: TrainingPlanRepository) {
    suspend operator fun invoke(ageGroupId: String, request: SaveTrainingPlanRequest): TrainingPlan =
        repository.saveTrainingPlan(ageGroupId, request)
}
