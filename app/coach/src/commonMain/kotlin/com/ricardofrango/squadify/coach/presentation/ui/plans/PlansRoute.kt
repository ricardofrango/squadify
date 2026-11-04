package com.ricardofrango.squadify.coach.presentation.ui.plans

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricardofrango.squadify.coach.domain.LoadPlansUseCase
import com.ricardofrango.squadify.coach.domain.OwnSpacePlan
import com.ricardofrango.squadify.coach.domain.RequestPlanUseCase
import com.ricardofrango.squadify.coach.domain.SaveStorePurchaseUseCase
import com.ricardofrango.squadify.coach.presentation.billing.PurchaseOutcome
import com.ricardofrango.squadify.coach.presentation.billing.rememberStoreBilling
import com.ricardofrango.squadify.coach.presentation.billing.storeReplacement
import com.ricardofrango.squadify.model.BillingPeriod
import com.ricardofrango.squadify.model.PlanChange
import com.ricardofrango.squadify.model.SpacePlan
import com.ricardofrango.squadify.model.StoreProduct
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject

/**
 * The Plans screen with what it changes plans through, as the coach server says ([PlanChange]): the store
 * (Google Play's payment sheet, then the server checks the purchase) when the server takes store purchases
 * and the phone has the store; otherwise the request (set at once on a local server, or by a platform
 * admin). A purchase the server hasn't seen (the app closed before sending it) is sent when Plans opens.
 * [onPlanChanged] when the plan itself changed, for Home's list.
 */
@Composable
fun PlansRoute(onPlanChanged: () -> Unit, onNavigateBack: () -> Unit) {
    val response by koinInject<OwnSpacePlan>().plans.collectAsStateWithLifecycle()
    val loadPlans = koinInject<LoadPlansUseCase>()
    val requestPlan = koinInject<RequestPlanUseCase>()
    val savePurchase = koinInject<SaveStorePurchaseUseCase>()
    val billing = rememberStoreBilling()

    // Loaded here too when they aren't yet (opened before Home, or the last load failed).
    var attempt by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(attempt) {
        if (response == null) {
            failed = false
            failed = !succeeds { loadPlans() }
        }
    }

    val owner = response?.currentPlan != null
    val byStore = owner && response?.changeBy == PlanChange.STORE && billing.available
    var prices by remember { mutableStateOf<Map<StoreProduct, String>>(emptyMap()) }
    LaunchedEffect(byStore, response?.plans) {
        val plans = response?.plans ?: return@LaunchedEffect
        if (!byStore) return@LaunchedEffect
        prices = runCatchingNotCancelled { billing.prices(plans.flatMap { it.storeProducts }) } ?: emptyMap()
        runCatchingNotCancelled {
            billing.owned().filter { !it.acknowledged }.forEach { if (savePurchase(it.productId, it.purchaseToken)) onPlanChanged() }
        }
    }

    suspend fun buy(plan: SpacePlan, period: BillingPeriod): Boolean {
        val current = response ?: return false
        val product = current.plans.firstOrNull { it.plan == plan }?.storeProducts?.firstOrNull { it.period == period } ?: return false
        val accountId = current.storeAccountId ?: return false
        val sold = current.plans.flatMap { it.storeProducts }.map { it.googleProductId }.toSet()
        val owned = billing.owned().firstOrNull { it.productId in sold }
        val replacement = storeReplacement(current.plans, current.currentPlan, current.currentPeriod, plan, period)
        return when (val outcome = billing.purchase(product, accountId, owned, replacement)) {
            is PurchaseOutcome.Bought -> {
                if (savePurchase(outcome.purchase.productId, outcome.purchase.purchaseToken)) onPlanChanged()
                true
            }
            PurchaseOutcome.Cancelled -> true
            PurchaseOutcome.Failed -> false
        }
    }

    PlansScreen(
        currentPlan = response?.currentPlan,
        requestedPlan = response?.requestedPlan,
        plans = response?.plans.orEmpty(),
        onRetry = if (failed) ({ attempt++ }) else null,
        // Only the owner of a space changes its plan.
        onRequestPlan = if (owner) { plan, period ->
            succeeds {
                if (byStore && period != null) {
                    check(buy(plan, period))
                } else if (requestPlan(plan)) {
                    onPlanChanged()
                }
            }
        } else null,
        onNavigateBack = onNavigateBack,
        prices = if (byStore) prices else emptyMap(),
        currentPeriod = response?.currentPeriod,
        pendingPlan = response?.pendingPlan,
        pendingFrom = response?.pendingFrom?.toLocalDateTime(TimeZone.currentSystemDefault())?.date
    )
}

private suspend fun succeeds(block: suspend () -> Unit): Boolean = runCatchingNotCancelled { block() } != null

private suspend fun <T> runCatchingNotCancelled(block: suspend () -> T): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}
