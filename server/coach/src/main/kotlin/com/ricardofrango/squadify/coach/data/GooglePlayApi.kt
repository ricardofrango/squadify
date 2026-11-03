package com.ricardofrango.squadify.coach.data

import com.google.auth.oauth2.GoogleCredentials
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import kotlin.time.Instant

/** A Google Play subscription purchase as Google tells it (`purchases.subscriptionsv2`). */
data class GoogleSubscription(
    // SUBSCRIPTION_STATE_ACTIVE, _CANCELED, _IN_GRACE_PERIOD, _ON_HOLD, _PAUSED, _EXPIRED, _PENDING, ...
    val state: String,
    val lineItems: List<GoogleLineItem>,
    // The purchase it replaced (an upgrade or downgrade), whose token stops counting.
    val linkedPurchaseToken: String?,
    val acknowledged: Boolean,
    // What the app set as the obfuscated account id: the buyer's user id.
    val accountId: String?,
    val test: Boolean
)

/** One subscription in a purchase: its product and base plan, until when it's paid, and what it goes down to at renewal. */
data class GoogleLineItem(val productId: String, val basePlanId: String?, val expiryTime: Instant?, val deferredProductId: String?)

/** Google Play's Developer API for the app's subscriptions (TECHNICAL_DECISIONS §72, payments). */
interface GooglePlayApi {
    /** Whether a service account is set, so purchases can be checked at all. */
    val configured: Boolean

    /** The purchase behind [purchaseToken]; null when Google doesn't know it. */
    suspend fun subscription(purchaseToken: String): GoogleSubscription?

    /** Tells Google the purchase was granted; unacknowledged ones are refunded after three days. */
    suspend fun acknowledge(productId: String, purchaseToken: String)
}

/**
 * [GooglePlayApi] over HTTP, signed in as the service account given ([serviceAccountJson], with access to
 * the app in Play Console) with the `androidpublisher` scope.
 */
class GooglePlayDeveloperApi(private val packageName: String, serviceAccountJson: String) : GooglePlayApi {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val client = HttpClient(CIO)
    private val json = Json { ignoreUnknownKeys = true }
    private val credentials: GoogleCredentials? = serviceAccountJson.takeIf { it.isNotBlank() }?.let {
        GoogleCredentials.fromStream(it.byteInputStream()).createScoped(listOf("https://www.googleapis.com/auth/androidpublisher"))
    }

    override val configured: Boolean get() = credentials != null

    private val base get() = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${packageName.encodeURLPathPart()}"

    private suspend fun token(): String {
        val c = credentials ?: error("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON isn't set")
        return withContext(Dispatchers.IO) {
            c.refreshIfExpired()
            c.accessToken.tokenValue
        }
    }

    override suspend fun subscription(purchaseToken: String): GoogleSubscription? {
        val response = client.get("$base/purchases/subscriptionsv2/tokens/${purchaseToken.encodeURLPathPart()}") { bearerAuth(token()) }
        if (response.status == HttpStatusCode.NotFound || response.status == HttpStatusCode.Gone) return null
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) error("Google Play answered ${response.status}: $body")
        return parseSubscription(json.parseToJsonElement(body).jsonObject)
    }

    override suspend fun acknowledge(productId: String, purchaseToken: String) {
        val response = client.post(
            "$base/purchases/subscriptions/${productId.encodeURLPathPart()}/tokens/${purchaseToken.encodeURLPathPart()}:acknowledge"
        ) { bearerAuth(token()) }
        if (!response.status.isSuccess()) logger.error("Acknowledging a Google Play purchase failed: {} {}", response.status, response.bodyAsText())
    }
}

/** A `SubscriptionPurchaseV2` from Google's JSON. */
internal fun parseSubscription(o: JsonObject): GoogleSubscription {
    fun JsonObject.string(key: String) = this[key]?.jsonPrimitive?.contentOrNull
    return GoogleSubscription(
        state = o.string("subscriptionState") ?: "SUBSCRIPTION_STATE_UNSPECIFIED",
        lineItems = o["lineItems"]?.jsonArray.orEmpty().map { it.jsonObject }.map { item ->
            GoogleLineItem(
                productId = item.string("productId").orEmpty(),
                basePlanId = item["offerDetails"]?.jsonObject?.string("basePlanId"),
                expiryTime = item.string("expiryTime")?.let(Instant::parse),
                deferredProductId = item["deferredItemReplacement"]?.jsonObject?.string("productId")
            )
        },
        linkedPurchaseToken = o.string("linkedPurchaseToken"),
        acknowledged = o.string("acknowledgementState") == "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
        accountId = o["externalAccountIdentifiers"]?.jsonObject?.string("obfuscatedExternalAccountId"),
        test = o.containsKey("testPurchase")
    )
}

