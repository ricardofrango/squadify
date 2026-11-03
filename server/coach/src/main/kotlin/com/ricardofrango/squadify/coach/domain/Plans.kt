package com.ricardofrango.squadify.coach.domain

import com.ricardofrango.squadify.coach.data.SpacePlanRepository
import com.ricardofrango.squadify.exception.PlanLimitException
import com.ricardofrango.squadify.model.PlanDetails
import com.ricardofrango.squadify.model.PlanFeature
import com.ricardofrango.squadify.model.SpacePlan

/** What each plan allows (REQUIREMENTS §5c), decided here only. Pro's training comes with training on the coach server. */
object PlanLimits {
    private val free = listOf(PlanFeature.LIVE_GAME, PlanFeature.CALENDAR, PlanFeature.HISTORY)
    private val basic = free + PlanFeature.STATS

    /**
     * Every plan, cheapest first, with all it has: the only place a plan's features and limits are
     * decided. The app gets it from `GET /v1/plans` and shows it, so changing a plan here needs no new app.
     */
    val catalogue = listOf(
        PlanDetails(SpacePlan.FREE, free, ageGroupLimit = 1, peoplePerAgeGroupLimit = 1),
        PlanDetails(SpacePlan.BASIC, basic, ageGroupLimit = 3, peoplePerAgeGroupLimit = 3),
        PlanDetails(SpacePlan.PRO, basic + PlanFeature.FOLLOWING_LIVE + PlanFeature.TRAINING)
    )

    fun of(plan: SpacePlan): PlanDetails = catalogue.first { it.plan == plan }

    /** Age groups a season; null: no limit. */
    fun ageGroups(plan: SpacePlan): Int? = of(plan).ageGroupLimit

    /** People on one age group, the owner included; null: no limit. */
    fun peoplePerAgeGroup(plan: SpacePlan): Int? = of(plan).peoplePerAgeGroupLimit

    /** Room for one more staff member on an age group with [staffCount] (the owner, not among them, takes a place too). */
    fun hasRoomForStaff(plan: SpacePlan, staffCount: Int): Boolean = peoplePerAgeGroup(plan)?.let { staffCount + 1 < it } ?: true

    fun stats(plan: SpacePlan): Boolean = PlanFeature.STATS in of(plan).features

    /** Following games live: live scores in the match lists, the live sockets and the live moments' notifications. */
    fun live(plan: SpacePlan): Boolean = PlanFeature.FOLLOWING_LIVE in of(plan).features
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
        if (staffFullOn(clubId, ageGroupId) != null) {
            throw PlanLimitException("The space's plan has no room for more staff on this age group")
        }
    }

    /** The space's plan when [ageGroupId] has no room for more staff on it; null while there's room. */
    suspend fun staffFullOn(clubId: String, ageGroupId: String): SpacePlan? =
        planOf(clubId).takeUnless { PlanLimits.hasRoomForStaff(it, plans.staffInJoinOrder(ageGroupId).size) }
}
