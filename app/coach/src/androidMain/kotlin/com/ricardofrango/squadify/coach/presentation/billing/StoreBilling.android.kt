package com.ricardofrango.squadify.coach.presentation.billing

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.ricardofrango.squadify.model.StoreProduct
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

@Composable
actual fun rememberStoreBilling(): StoreBilling {
    val context = LocalContext.current
    val billing = remember(context) { PlayStoreBilling(context) }
    DisposableEffect(billing) { onDispose { billing.close() } }
    return billing
}

/**
 * [StoreBilling] on Google Play Billing: a plan is a subscription with a monthly and a yearly base plan
 * ([StoreProduct]); a change of plan or period replaces the current purchase with Google's replacement
 * modes. The result of the payment sheet comes to the listener, handed to the [purchase] waiting for it.
 */
private class PlayStoreBilling(private val context: Context) : StoreBilling {
    private var pending: CompletableDeferred<PurchaseOutcome>? = null
    private val connecting = Mutex()

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener { result, purchases ->
            val outcome = when (result.responseCode) {
                BillingClient.BillingResponseCode.OK -> purchases?.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                    ?.let { PurchaseOutcome.Bought(it.owned()) } ?: PurchaseOutcome.Failed
                BillingClient.BillingResponseCode.USER_CANCELED -> PurchaseOutcome.Cancelled
                else -> PurchaseOutcome.Failed
            }
            pending?.complete(outcome)
        }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    override val available = true

    fun close() = client.endConnection()

    private suspend fun connected(): Boolean = connecting.withLock {
        if (client.isReady) return true
        suspendCancellableCoroutine { continuation ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (continuation.isActive) continuation.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                }

                override fun onBillingServiceDisconnected() {
                    if (continuation.isActive) continuation.resume(false)
                }
            })
        }
    }

    private suspend fun details(productIds: Collection<String>): List<ProductDetails> {
        if (productIds.isEmpty() || !connected()) return emptyList()
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            productIds.distinct().map {
                QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(BillingClient.ProductType.SUBS).build()
            }
        ).build()
        return client.queryProductDetails(params).productDetailsList.orEmpty()
    }

    /** The base plan's own offer (no introductory one) of [product]. */
    private fun ProductDetails.baseOffer(product: StoreProduct) =
        subscriptionOfferDetails.orEmpty().firstOrNull { it.basePlanId == product.googleBasePlanId && it.offerId == null }

    override suspend fun prices(products: List<StoreProduct>): Map<StoreProduct, String> {
        val found = details(products.map { it.googleProductId }).associateBy { it.productId }
        return products.mapNotNull { product ->
            val offer = found[product.googleProductId]?.baseOffer(product) ?: return@mapNotNull null
            offer.pricingPhases.pricingPhaseList.lastOrNull()?.formattedPrice?.let { product to it }
        }.toMap()
    }

    override suspend fun owned(): List<OwnedPurchase> {
        if (!connected()) return emptyList()
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        return client.queryPurchasesAsync(params).purchasesList
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            .map { it.owned() }
    }

    override suspend fun purchase(product: StoreProduct, accountId: String, current: OwnedPurchase?, replacement: Replacement): PurchaseOutcome {
        val activity = context.activity() ?: return PurchaseOutcome.Failed
        val details = details(listOf(product.googleProductId)).firstOrNull() ?: return PurchaseOutcome.Failed
        val offer = details.baseOffer(product) ?: return PurchaseOutcome.Failed
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).setOfferToken(offer.offerToken).build())
            )
            .setObfuscatedAccountId(accountId)
        if (current != null) {
            params.setSubscriptionUpdateParams(
                BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                    .setOldPurchaseToken(current.purchaseToken)
                    .setSubscriptionReplacementMode(
                        when (replacement) {
                            Replacement.UP -> BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.CHARGE_PRORATED_PRICE
                            Replacement.LONGER -> BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.WITH_TIME_PRORATION
                            Replacement.DOWN -> BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.DEFERRED
                        }
                    )
                    .build()
            )
        }
        val waiting = CompletableDeferred<PurchaseOutcome>().also { pending = it }
        val launched = client.launchBillingFlow(activity, params.build())
        if (launched.responseCode != BillingClient.BillingResponseCode.OK) return PurchaseOutcome.Failed
        return waiting.await()
    }
}

private fun Purchase.owned() = OwnedPurchase(products.firstOrNull().orEmpty(), purchaseToken, isAcknowledged)

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
