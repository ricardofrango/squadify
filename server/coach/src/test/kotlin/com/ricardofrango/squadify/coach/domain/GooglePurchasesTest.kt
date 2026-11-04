package com.ricardofrango.squadify.coach.domain

import com.ricardofrango.squadify.coach.data.GoogleLineItem
import com.ricardofrango.squadify.coach.data.GooglePlayApi
import com.ricardofrango.squadify.coach.data.GoogleSubscription
import com.ricardofrango.squadify.coach.data.SpacePlanRepository
import com.ricardofrango.squadify.coach.data.StoreSubscriptionRepository
import com.ricardofrango.squadify.coach.data.StoredSubscription
import com.ricardofrango.squadify.coach.data.parseSubscription
import com.ricardofrango.squadify.coach.routes.googleNotification
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.GooglePurchaseRequest
import com.ricardofrango.squadify.model.PlansResponse
import com.ricardofrango.squadify.model.SpacePlan
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import kotlin.io.encoding.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

// Payments on Google Play (REQUIREMENTS §5c): a purchase is read from Google, never trusted from the app;
// the space's plan is the highest one an entitled subscription gives, Free once none does; staging takes
// only test purchases, production only real ones; and Google's notifications move the plan.
class GooglePurchasesTest {
    private val now = Instant.parse("2026-11-03T10:00:00Z")
    private val clock = object : Clock { override fun now() = now }
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }

    /** Google, as the test sets it: purchases by token. */
    private class FakeGoogle : GooglePlayApi {
        val purchases = mutableMapOf<String, GoogleSubscription>()
        val acknowledged = mutableListOf<String>()
        override val configured = true
        override suspend fun subscription(purchaseToken: String) = purchases[purchaseToken]
        override suspend fun acknowledge(productId: String, purchaseToken: String) { acknowledged += purchaseToken }
    }

    private class Subscriptions : StoreSubscriptionRepository {
        val rows = mutableMapOf<String, StoredSubscription>()
        override suspend fun byToken(purchaseToken: String) = rows[purchaseToken]
        override suspend fun forSpace(clubId: String) = rows.values.filter { it.clubId == clubId }
        override suspend fun save(subscription: StoredSubscription, at: Instant) { rows[subscription.purchaseToken] = subscription }
    }

    private class Plans : SpacePlanRepository by mockk(relaxed = true) {
        var plan = PlanLimits.FREE
        override suspend fun planOf(clubId: String) = plan
        override suspend fun setPlan(clubId: String, plan: SpacePlan, at: Instant) { this.plan = plan }
    }

    private val google = FakeGoogle()
    private val subscriptions = Subscriptions()
    private val plans = Plans()
    private val clubs = mockk<ClubRepository>().also { repo ->
        coEvery { repo.getMemberships("ana") } returns listOf(mockk<Club> { every { id } returns "space1" } to ClubRole.CLUB_ADMIN)
        coEvery { repo.getMemberships("staff") } returns emptyList()
    }

    private fun sync(acceptsTest: Boolean = true, acceptsReal: Boolean = true) =
        GoogleSubscriptions(google, subscriptions, plans, acceptsTest, acceptsReal, clock)

    private fun verify(acceptsTest: Boolean = true, acceptsReal: Boolean = true): VerifyGooglePurchaseUseCase {
        val getPlans = mockk<GetPlansUseCase>()
        coEvery { getPlans(any(), any()) } answers { PlansResponse(emptyList(), plans.plan) }
        return VerifyGooglePurchaseUseCase(clubs, google, sync(acceptsTest, acceptsReal), getPlans, transactionManager)
    }

    private fun purchase(
        product: String, state: String = "SUBSCRIPTION_STATE_ACTIVE", expiry: Instant = now + 30.days,
        linked: String? = null, deferred: String? = null, account: String? = "ana", test: Boolean = true
    ) = GoogleSubscription(state, listOf(GoogleLineItem(product, "monthly", expiry, deferred)), linked, acknowledged = false, accountId = account, test = test)

    @Test
    fun aPurchaseSetsThePlanAndIsAcknowledged() = runBlocking {
        google.purchases["t1"] = purchase("coach_basic")
        assertEquals(PlanLimits.BASIC, verify()("ana", GooglePurchaseRequest("coach_basic", "t1")).currentPlan)
        assertEquals(listOf("t1"), google.acknowledged)

        // Going up replaces it: the old purchase stops counting.
        google.purchases["t2"] = purchase("coach_pro", linked = "t1")
        assertEquals(PlanLimits.PRO, verify()("ana", GooglePurchaseRequest("coach_pro", "t2")).currentPlan)
        assertFalse(subscriptions.rows.getValue("t1").entitled)
    }

    @Test
    fun onlyTheOwnerWithTheirOwnPurchaseOfAPlan() = runBlocking {
        google.purchases["t1"] = purchase("coach_pro")
        assertFailsWith<ForbiddenException> { verify()("staff", GooglePurchaseRequest("coach_pro", "t1")) }
        assertFailsWith<ValidationException> { verify()("ana", GooglePurchaseRequest("coach_gold", "t1")) }
        assertFailsWith<ValidationException> { verify()("ana", GooglePurchaseRequest("coach_pro", "unknown")) }
        google.purchases["t2"] = purchase("coach_pro", account = "someone-else")
        assertFailsWith<ForbiddenException> { verify()("ana", GooglePurchaseRequest("coach_pro", "t2")) }
        assertEquals(PlanLimits.FREE, plans.plan)
    }

    @Test
    fun stagingTakesTestPurchasesAndProductionRealOnes() = runBlocking {
        google.purchases["test"] = purchase("coach_pro", test = true)
        google.purchases["real"] = purchase("coach_pro", test = false)
        // Production.
        assertFailsWith<ValidationException> { verify(acceptsTest = false)("ana", GooglePurchaseRequest("coach_pro", "test")) }
        // Staging.
        assertFailsWith<ValidationException> { verify(acceptsReal = false)("ana", GooglePurchaseRequest("coach_pro", "real")) }
        assertEquals(PlanLimits.FREE, plans.plan)
        assertEquals(PlanLimits.PRO, verify(acceptsReal = false)("ana", GooglePurchaseRequest("coach_pro", "test")).currentPlan)
    }

    @Test
    fun notificationsMoveThePlan() = runBlocking {
        val handle = HandleGoogleNotificationUseCase(clubs, google, subscriptions, sync(), transactionManager)
        // A purchase the app never sent: placed in the buyer's own space.
        google.purchases["t1"] = purchase("coach_pro")
        handle("t1")
        assertEquals(PlanLimits.PRO, plans.plan)

        // Going down at the renewal: Pro until then, Basic shown as coming.
        google.purchases["t1"] = purchase("coach_pro", deferred = "coach_basic")
        handle("t1")
        assertEquals(PlanLimits.PRO, plans.plan)
        assertEquals(PlanLimits.BASIC, subscriptions.rows.getValue("t1").pendingPlan)

        // Cancelled: still Pro while paid. Expired: back to Free.
        google.purchases["t1"] = purchase("coach_pro", state = "SUBSCRIPTION_STATE_CANCELED")
        handle("t1")
        assertEquals(PlanLimits.PRO, plans.plan)
        google.purchases["t1"] = purchase("coach_pro", state = "SUBSCRIPTION_STATE_EXPIRED", expiry = now - 1.days)
        handle("t1")
        assertEquals(PlanLimits.FREE, plans.plan)

        // A buyer with no space of their own: left alone.
        google.purchases["t9"] = purchase("coach_pro", account = "staff")
        handle("t9")
        assertNull(subscriptions.rows["t9"])
    }

    @Test
    fun googlesJsonIsRead() {
        val json = """
            {"subscriptionState":"SUBSCRIPTION_STATE_ACTIVE","linkedPurchaseToken":"old","acknowledgementState":"ACKNOWLEDGEMENT_STATE_PENDING",
             "externalAccountIdentifiers":{"obfuscatedExternalAccountId":"ana"},"testPurchase":{},
             "lineItems":[{"productId":"coach_pro","expiryTime":"2026-12-03T10:00:00.123Z","offerDetails":{"basePlanId":"monthly"},
                           "deferredItemReplacement":{"productId":"coach_basic"}}]}
        """.trimIndent()
        val parsed = parseSubscription(Json.parseToJsonElement(json).jsonObject)
        assertEquals(GoogleLineItem("coach_pro", "monthly", Instant.parse("2026-12-03T10:00:00.123Z"), "coach_basic"), parsed.lineItems.single())
        assertEquals("old", parsed.linkedPurchaseToken)
        assertEquals("ana", parsed.accountId)
        assertTrue(parsed.test)
        assertFalse(parsed.acknowledged)

        val data = Base64.encode("""{"packageName":"com.ricardofrango.squadify.coach","subscriptionNotification":{"notificationType":2,"purchaseToken":"t1"}}""".encodeToByteArray())
        val pushed = googleNotification("""{"message":{"data":"$data"},"subscription":"projects/x/subscriptions/y"}""")
        assertEquals("t1", pushed?.purchaseToken)
        assertEquals("com.ricardofrango.squadify.coach", pushed?.packageName)
        assertNull(googleNotification("not json"))
    }
}
