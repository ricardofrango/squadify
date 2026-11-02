package com.ricardofrango.squadify.coach.domain

import com.ricardofrango.squadify.coach.data.SpacePlanRepository
import com.ricardofrango.squadify.exception.PlanLimitException
import com.ricardofrango.squadify.model.SpacePlan

/** What each plan allows (REQUIREMENTS §5c). Pro's training comes with training on the coach server. */
object PlanLimits {
    /** Age groups a season; null: no limit. */
    fun ageGroups(plan: SpacePlan): Int? = when (plan) {
        SpacePlan.FREE -> 1
        SpacePlan.BASIC -> 3
        SpacePlan.PRO -> null
    }

    /** People on one age group, the owner included; null: no limit. */
    fun peoplePerAgeGroup(plan: SpacePlan): Int? = when (plan) {
        SpacePlan.FREE -> 1
        SpacePlan.BASIC -> 3
        SpacePlan.PRO -> null
    }

    fun stats(plan: SpacePlan): Boolean = plan != SpacePlan.FREE
}

/**
 * A space's plan and what's locked by it. Going down a plan deletes nothing: in each season the first
 * age groups created stay in use and the others are locked, and on each age group the first staff to
 * join keep access and the others lose it, until the plan allows them again. Call inside a transaction.
 */
class CoachPlans(private val plans: SpacePlanRepository) {
    suspend fun planOf(clubId: String): SpacePlan = plans.planOf(clubId)

    /** The space's age groups over the plan's limit. */
    suspend fun lockedAgeGroups(clubId: String, plan: SpacePlan): Set<String> {
        val limit = PlanLimits.ageGroups(plan) ?: return emptySet()
        return plans.ageGroupsInCreationOrder(clubId)
            .groupBy({ (_, seasonId) -> seasonId }, { (id, _) -> id })
            .values.flatMap { it.drop(limit) }
            .toSet()
    }

    /** [ageGroupId]'s staff over the plan's limit; the owner takes one of its places. */
    suspend fun lockedStaff(ageGroupId: String, plan: SpacePlan): Set<String> {
        val limit = PlanLimits.peoplePerAgeGroup(plan) ?: return emptySet()
        return plans.staffInJoinOrder(ageGroupId).drop(limit - 1).toSet()
    }

    /** Room for one more staff member on [ageGroupId]: for a new code, and when one is used. */
    suspend fun requireRoomForStaff(clubId: String, ageGroupId: String) {
        val limit = PlanLimits.peoplePerAgeGroup(planOf(clubId)) ?: return
        if (plans.staffInJoinOrder(ageGroupId).size + 1 >= limit) {
            throw PlanLimitException("The space's plan has no room for more staff on this age group")
        }
    }
}
