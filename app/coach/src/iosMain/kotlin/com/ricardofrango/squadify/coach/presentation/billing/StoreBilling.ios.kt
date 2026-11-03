package com.ricardofrango.squadify.coach.presentation.billing

import androidx.compose.runtime.Composable
import com.ricardofrango.squadify.model.StoreProduct

// The App Store's subscriptions come in their own change (REQUIREMENTS, payments); until then iOS asks for plans.
private object NoStoreBilling : StoreBilling {
    override val available = false
    override suspend fun prices(products: List<StoreProduct>): Map<StoreProduct, String> = emptyMap()
    override suspend fun owned(): List<OwnedPurchase> = emptyList()
    override suspend fun purchase(product: StoreProduct, accountId: String, current: OwnedPurchase?, replacement: Replacement) = PurchaseOutcome.Failed
}

@Composable
actual fun rememberStoreBilling(): StoreBilling = NoStoreBilling
