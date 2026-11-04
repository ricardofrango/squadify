package com.ricardofrango.squadify.coach.domain

import com.ricardofrango.squadify.coach.data.GoogleSubscription
import com.ricardofrango.squadify.coach.data.GooglePlayApi
import com.ricardofrango.squadify.coach.data.SpacePlanRepository
import com.ricardofrango.squadify.coach.data.StoreSubscriptionRepository
import com.ricardofrango.squadify.coach.data.StoredSubscription
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.GooglePurchaseRequest
import com.ricardofrango.squadify.model.PlansResponse
import org.slf4j.LoggerFactory
import kotlin.time.Clock

/**
 * A space's plan from its Google Play subscriptions (REQUIREMENTS §5c, payments; TECHNICAL_DECISIONS §72).
 * Each purchase is read from Google, never from the app, and stored as Google tells it; the space's plan
 * is then the highest one an entitled subscription gives (active, or cancelled but paid until its expiry),
 * or Free once none does. A purchase that replaced another (going up, or down at the renewal) ends the
 * old one. Only [acceptsTest] test purchases (license testers, nothing charged) and [acceptsReal] real
 * ones count: staging takes only the first, production only the second.
 */
class GoogleSubscriptions(
    private val google: GooglePlayApi,
    private val subscriptions: StoreSubscriptionRepository,
    private val spacePlans: SpacePlanRepository,
    private val acceptsTest: Boolean,
    private val acceptsReal: Boolean,
    private val clock: Clock = Clock.System
) {
    fun accepts(purchase: GoogleSubscription): Boolean = if (purchase.test) acceptsTest else acceptsReal

    /** Stores [purchase] for [clubId] and sets the space's plan from its subscriptions. Call inside a transaction. */
    suspend fun apply(purchaseToken: String, purchase: GoogleSubscription, clubId: String, userId: String) {
        val item = purchase.lineItems.firstOrNull() ?: return
        val plan = PlanLimits.forGoogleProduct(item.productId) ?: return
        val now = clock.now()
        val entitled = purchase.state in ENTITLED_STATES && (item.expiryTime == null || item.expiryTime > now)
        subscriptions.save(
            StoredSubscription(
                purchaseToken, clubId, userId, STORE, item.productId, item.basePlanId, plan, entitled, item.expiryTime,
                pendingPlan = item.deferredProductId?.let(PlanLimits::forGoogleProduct), test = purchase.test
            ),
            now
        )
        purchase.linkedPurchaseToken?.let { old -> subscriptions.byToken(old)?.let { subscriptions.save(it.copy(entitled = false), now) } }
        val best = subscriptions.forSpace(clubId).filter { it.entitled }.maxByOrNull { PlanLimits.rank(it.plan) }?.plan ?: PlanLimits.FREE
        if (spacePlans.planOf(clubId) != best) spacePlans.setPlan(clubId, best, now)
    }

    /** Tells Google the purchase was granted, once it gives the plan. */
    suspend fun acknowledge(purchaseToken: String, purchase: GoogleSubscription) {
        val item = purchase.lineItems.firstOrNull() ?: return
        if (!purchase.acknowledged && purchase.state in ENTITLED_STATES) google.acknowledge(item.productId, purchaseToken)
    }

    companion object {
        const val STORE = "GOOGLE"
        val ENTITLED_STATES = setOf("SUBSCRIPTION_STATE_ACTIVE", "SUBSCRIPTION_STATE_CANCELED", "SUBSCRIPTION_STATE_IN_GRACE_PERIOD")
    }
}

/**
 * `POST /v1/plans/purchases/google`: the owner bought or changed a subscription in the app. Checked with
 * Google: a product of one of the plans, bought by this account (the app sets its user id on the
 * purchase), test or real as this server accepts; then the plan is set and the purchase acknowledged.
 * Returns the plans, as `GET /v1/plans` does, in [language].
 */
class VerifyGooglePurchaseUseCase(
    private val clubRepository: ClubRepository,
    private val google: GooglePlayApi,
    private val googleSubscriptions: GoogleSubscriptions,
    private val getPlansUseCase: GetPlansUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, request: GooglePurchaseRequest, language: String? = null): PlansResponse {
        val spaceId = transactionManager.runInTransaction {
            clubRepository.getMemberships(userId).firstOrNull { (_, role) -> role == ClubRole.CLUB_ADMIN }?.first?.id
        } ?: throw ForbiddenException("Only a space's owner can subscribe")
        if (PlanLimits.forGoogleProduct(request.productId) == null) throw ValidationException("No plan is sold as ${request.productId}")
        if (!google.configured) throw ValidationException("This server can't check store purchases")
        val purchase = google.subscription(request.purchaseToken) ?: throw ValidationException("Google doesn't know this purchase")
        if (purchase.accountId != null && purchase.accountId != userId) throw ForbiddenException("This purchase belongs to another account")
        if (!googleSubscriptions.accepts(purchase)) {
            throw ValidationException(if (purchase.test) "Test purchases don't count on this server" else "Only test purchases count on this server")
        }
        transactionManager.runInTransaction { googleSubscriptions.apply(request.purchaseToken, purchase, spaceId, userId) }
        googleSubscriptions.acknowledge(request.purchaseToken, purchase)
        return getPlansUseCase(userId, language)
    }
}

/**
 * Google Play's real-time developer notification for a subscription (renewed, cancelled, on hold,
 * expired, refunded, replaced...), through Pub/Sub: the purchase is read again from Google and applied to
 * the space it pays for (the one already stored, or the buyer's own for a purchase the server hasn't seen).
 * A purchase it can't place, or of a kind this server doesn't accept, is left alone.
 */
class HandleGoogleNotificationUseCase(
    private val clubRepository: ClubRepository,
    private val google: GooglePlayApi,
    private val subscriptions: StoreSubscriptionRepository,
    private val googleSubscriptions: GoogleSubscriptions,
    private val transactionManager: TransactionManager
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    suspend operator fun invoke(purchaseToken: String) {
        val purchase = google.subscription(purchaseToken) ?: return logger.warn("A Google Play notification for a purchase Google doesn't know")
        if (!googleSubscriptions.accepts(purchase)) return
        val applied = transactionManager.runInTransaction {
            val stored = subscriptions.byToken(purchaseToken)
            val userId = stored?.userId ?: purchase.accountId ?: return@runInTransaction false
            val clubId = stored?.clubId
                ?: clubRepository.getMemberships(userId).firstOrNull { (_, role) -> role == ClubRole.CLUB_ADMIN }?.first?.id
                ?: return@runInTransaction false
            googleSubscriptions.apply(purchaseToken, purchase, clubId, userId)
            true
        }
        if (applied) googleSubscriptions.acknowledge(purchaseToken, purchase)
        else logger.warn("A Google Play notification for a purchase with no space to give it to")
    }
}
