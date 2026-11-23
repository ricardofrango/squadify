package com.sportssoftw.squadify.domain.repository

import com.sportssoftw.squadify.model.TrainingPlan

interface TrainingPlanRepository {
    suspend fun getByAgeGroup(ageGroupId: String): List<TrainingPlan>
    // Every age group's plans at once, for the club Home's missing-plan alerts.
    suspend fun getByAgeGroups(ageGroupIds: List<String>): List<TrainingPlan>
    // Inserts, or replaces the plan with the same age group, level and period start.
    suspend fun save(plan: TrainingPlan, updatedBy: String): TrainingPlan
}
