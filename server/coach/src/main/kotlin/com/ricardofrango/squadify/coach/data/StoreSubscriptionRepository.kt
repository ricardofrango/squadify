package com.ricardofrango.squadify.coach.data

import com.ricardofrango.squadify.model.SpacePlan
import com.ricardofrango.squadify.util.toUuid
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import kotlin.time.Instant

/** A store subscription as last told by the store (`store_subscriptions`). */
data class StoredSubscription(
    val purchaseToken: String,
    val clubId: String,
    val userId: String,
    val store: String,
    val productId: String,
    val basePlanId: String?,
    val plan: SpacePlan,
    val entitled: Boolean,
    val expiresAt: Instant?,
    val pendingPlan: SpacePlan?,
    val test: Boolean
)

/** The spaces' store subscriptions (REQUIREMENTS §5c, payments). Called inside the callers' transactions. */
interface StoreSubscriptionRepository {
    suspend fun byToken(purchaseToken: String): StoredSubscription?
    suspend fun forSpace(clubId: String): List<StoredSubscription>
    suspend fun save(subscription: StoredSubscription, at: Instant)
}

class ExposedStoreSubscriptionRepository : StoreSubscriptionRepository {
    override suspend fun byToken(purchaseToken: String): StoredSubscription? =
        StoreSubscriptions.selectAll().where { StoreSubscriptions.purchaseToken eq purchaseToken }.singleOrNull()?.let(::toModel)

    override suspend fun forSpace(clubId: String): List<StoredSubscription> =
        StoreSubscriptions.selectAll().where { StoreSubscriptions.clubId eq clubId.toUuid() }.map(::toModel)

    override suspend fun save(subscription: StoredSubscription, at: Instant) {
        StoreSubscriptions.upsert {
            it[purchaseToken] = subscription.purchaseToken
            it[clubId] = subscription.clubId.toUuid()
            it[userId] = subscription.userId.toUuid()
            it[store] = subscription.store
            it[productId] = subscription.productId
            it[basePlanId] = subscription.basePlanId
            it[plan] = subscription.plan.id
            it[entitled] = subscription.entitled
            it[expiresAt] = subscription.expiresAt
            it[pendingPlan] = subscription.pendingPlan?.id
            it[test] = subscription.test
            it[updatedAt] = at
        }
    }

    private fun toModel(row: org.jetbrains.exposed.v1.core.ResultRow) = StoredSubscription(
        purchaseToken = row[StoreSubscriptions.purchaseToken],
        clubId = row[StoreSubscriptions.clubId].toString(),
        userId = row[StoreSubscriptions.userId].toString(),
        store = row[StoreSubscriptions.store],
        productId = row[StoreSubscriptions.productId],
        basePlanId = row[StoreSubscriptions.basePlanId],
        plan = SpacePlan(row[StoreSubscriptions.plan]),
        entitled = row[StoreSubscriptions.entitled],
        expiresAt = row[StoreSubscriptions.expiresAt],
        pendingPlan = row[StoreSubscriptions.pendingPlan]?.let(::SpacePlan),
        test = row[StoreSubscriptions.test]
    )
}
