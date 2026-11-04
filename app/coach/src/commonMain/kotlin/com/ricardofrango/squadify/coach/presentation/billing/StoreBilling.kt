package com.ricardofrango.squadify.coach.presentation.billing

import androidx.compose.runtime.Composable
import com.ricardofrango.squadify.model.BillingPeriod
import com.ricardofrango.squadify.model.PlanDetails
import com.ricardofrango.squadify.model.SpacePlan
import com.ricardofrango.squadify.model.StoreProduct

/** A subscription the store says this phone's account has: its product and purchase token. */
data class OwnedPurchase(val productId: String, val purchaseToken: String, val acknowledged: Boolean)

/**
 * How a subscription replaces the one the coach has (REQUIREMENTS §5c): going up is at once, charging the
 * difference for the days left ([UP]; a longer period at once, the days left credited, [LONGER]); going
 * down waits for the renewal ([DOWN]).
 */
enum class Replacement { UP, LONGER, DOWN }

sealed interface PurchaseOutcome {
    data class Bought(val purchase: OwnedPurchase) : PurchaseOutcome
    data object Cancelled : PurchaseOutcome
    data object Failed : PurchaseOutcome
}

/**
 * The phone's store, for the plans' subscriptions (TECHNICAL_DECISIONS §72, payments): Google Play on
 * Android; none on iOS yet ([available] false: the app asks for plans instead). What a purchase gives is
 * never decided here: the coach server checks it with the store.
 */
interface StoreBilling {
    val available: Boolean

    /** The store's price of each product, formatted for the phone ("4,99 €"); products it doesn't sell are left out. */
    suspend fun prices(products: List<StoreProduct>): Map<StoreProduct, String>

    /** The subscriptions the store says the phone's account has. */
    suspend fun owned(): List<OwnedPurchase>

    /**
     * The store's payment sheet for [product], tagged with [accountId] (the server only accepts its own
     * owner's), replacing [current] by [replacement] when there's one.
     */
    suspend fun purchase(product: StoreProduct, accountId: String, current: OwnedPurchase?, replacement: Replacement): PurchaseOutcome
}

@Composable
expect fun rememberStoreBilling(): StoreBilling

/**
 * How moving from [current] (on [currentPeriod]) to [target] on [period] replaces the subscription, the
 * plans ranked as the server lists them (cheapest first): a higher plan at once ([Replacement.UP], or
 * [Replacement.LONGER] onto a yearly one), the same plan from monthly to yearly at once too; anything else,
 * a lower plan or from yearly to monthly, at the renewal ([Replacement.DOWN]).
 */
fun storeReplacement(plans: List<PlanDetails>, current: SpacePlan?, currentPeriod: BillingPeriod?, target: SpacePlan, period: BillingPeriod): Replacement {
    val from = plans.indexOfFirst { it.plan == current }
    val to = plans.indexOfFirst { it.plan == target }
    val toYearly = currentPeriod == BillingPeriod.MONTHLY && period == BillingPeriod.YEARLY
    val toMonthly = currentPeriod == BillingPeriod.YEARLY && period == BillingPeriod.MONTHLY
    return when {
        toMonthly || to < from -> Replacement.DOWN
        toYearly -> Replacement.LONGER
        else -> Replacement.UP
    }
}
