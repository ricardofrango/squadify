package com.ricardofrango.squadify.coach.domain

import com.ricardofrango.squadify.coach.data.SpacePlanRepository
import com.ricardofrango.squadify.exception.PlanLimitException
import com.ricardofrango.squadify.model.BillingPeriod
import com.ricardofrango.squadify.model.PlanDetails
import com.ricardofrango.squadify.model.PlanFeature
import com.ricardofrango.squadify.model.PlanFeatureLine
import com.ricardofrango.squadify.model.SpacePlan
import com.ricardofrango.squadify.model.StoreProduct

/**
 * Every plan and what it allows (REQUIREMENTS §5c), decided here only: its name, its features (with
 * how the app lists them, in English and Portuguese) and its limits. The app gets them from `GET
 * /v1/plans` and shows them as they come, so adding or changing a plan here needs no new app. A space
 * on a plan that's no longer here counts as [FREE].
 */
object PlanLimits {
    val FREE = SpacePlan("FREE")
    val BASIC = SpacePlan("BASIC")
    val PRO = SpacePlan("PRO")

    /** A feature's line on the Plans screen: English, Portuguese. */
    private val featureTexts = mapOf(
        PlanFeature.LIVE_GAME to ("Live game recording" to "Gravação de jogos ao vivo"),
        PlanFeature.CALENDAR to ("Calendar" to "Calendário"),
        PlanFeature.HISTORY to ("Match history" to "Histórico de jogos"),
        PlanFeature.STATS to ("Stats: age group, team, player, full stats and match stats" to "Estatísticas: escalão, equipa, jogador, estatísticas completas e de jogo"),
        PlanFeature.FOLLOWING_LIVE to ("Live game results and notifications" to "Resultados e notificações dos jogos ao vivo"),
        PlanFeature.TRAINING to ("Training: sessions, exercises, player ratings, training stats and plans" to "Treino: sessões, exercícios, avaliação dos jogadores, estatísticas de treino e planeamento"),
        PlanFeature.ADS to ("With ads (never during a live game)" to "Com anúncios (nunca durante um jogo ao vivo)"),
        PlanFeature.NO_ADS to ("No ads" to "Sem anúncios")
    )

    /** On Google Play each paid plan is a subscription ([googleProduct]) with a monthly and a yearly base plan. */
    private class Plan(val plan: SpacePlan, val name: String, val features: List<String>, val ageGroups: Int?, val people: Int?, val googleProduct: String? = null, val ads: Boolean = false) {
        val storeProducts: List<StoreProduct> = googleProduct?.let { id ->
            listOf(StoreProduct(BillingPeriod.MONTHLY, id, "monthly"), StoreProduct(BillingPeriod.YEARLY, id, "yearly"))
        }.orEmpty()
    }

    private val freeFeatures = listOf(PlanFeature.LIVE_GAME, PlanFeature.CALENDAR, PlanFeature.HISTORY)
    private val basicFeatures = freeFeatures + PlanFeature.STATS

    /** Cheapest first, each with all it has. */
    private val plans = listOf(
        // The ads line is each plan's own (the developer, 2026-11-06): Free's says it has them, but never
        // in a live game; the paid ones, none. The Plans card lists what a plan adds to the one before, so
        // Basic shows "No ads" and Pro has it from Basic.
        Plan(FREE, "Free", freeFeatures + PlanFeature.ADS, ageGroups = 1, people = 1, ads = true),
        Plan(BASIC, "Basic", basicFeatures + PlanFeature.NO_ADS, ageGroups = 3, people = 3, googleProduct = "coach_basic"),
        Plan(PRO, "Pro", basicFeatures + PlanFeature.NO_ADS + PlanFeature.FOLLOWING_LIVE + PlanFeature.TRAINING, ageGroups = null, people = null, googleProduct = "coach_pro")
    )

    private fun planFor(plan: SpacePlan): Plan = plans.firstOrNull { it.plan == plan } ?: plans.first { it.plan == FREE }

    /** The plan a Google Play subscription is for; null for a product that isn't one of them. */
    fun forGoogleProduct(productId: String): SpacePlan? = plans.firstOrNull { it.googleProduct == productId }?.plan

    /** Where [plan] stands, cheapest first (an unknown plan as Free): of two plans a space has, the higher counts. */
    fun rank(plan: SpacePlan): Int = plans.indexOf(planFor(plan))

    /** Whether [plan] is one of the plans, for the admin who sets it. */
    fun exists(plan: SpacePlan): Boolean = plans.any { it.plan == plan }

    /** Every plan for the app, its features in [language] ("pt" for Portuguese; anything else is English). */
    fun catalogue(language: String?): List<PlanDetails> {
        val portuguese = language?.lowercase()?.startsWith("pt") == true
        return plans.map { plan ->
            PlanDetails(
                plan.plan,
                plan.name,
                plan.features.map { key -> PlanFeatureLine(key, featureTexts.getValue(key).let { if (portuguese) it.second else it.first }) },
                plan.ageGroups,
                plan.people,
                plan.storeProducts
            )
        }
    }

    /** Whether a space on [plan] shows ads in the app (Free; REQUIREMENTS §5c). */
    fun showsAds(plan: SpacePlan): Boolean = planFor(plan).ads

    /** Age groups a season; null: no limit. */
    fun ageGroups(plan: SpacePlan): Int? = planFor(plan).ageGroups

    /** People on one age group, the owner included; null: no limit. */
    fun peoplePerAgeGroup(plan: SpacePlan): Int? = planFor(plan).people

    /** Room for one more staff member on an age group with [staffCount] (the owner, not among them, takes a place too). */
    fun hasRoomForStaff(plan: SpacePlan, staffCount: Int): Boolean = peoplePerAgeGroup(plan)?.let { staffCount + 1 < it } ?: true

    fun stats(plan: SpacePlan): Boolean = PlanFeature.STATS in planFor(plan).features

    /** Training: sessions, exercises, ratings, training stats and plans. */
    fun training(plan: SpacePlan): Boolean = PlanFeature.TRAINING in planFor(plan).features

    /** Following games live: live scores in the match lists, the live sockets and the live moments' notifications. */
    fun live(plan: SpacePlan): Boolean = PlanFeature.FOLLOWING_LIVE in planFor(plan).features
}

/**
 * A space's plan and what's locked by it. Going down a plan deletes nothing: in each season the first
 * age groups created stay in use and the others are locked, and on each age group the first staff to
 * join keep access and the others lose it, until the plan allows them again. Call inside a transaction.
 */
class CoachPlans(private val plans: SpacePlanRepository) {
    suspend fun planOf(clubId: String): SpacePlan = plans.planOf(clubId)

    /** The plan the space's owner asked for and hasn't got yet; null: none. */
    suspend fun requestedPlan(clubId: String): SpacePlan? = plans.requestedPlan(clubId)

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
