package com.ricardofrango.squadify.coach.routes

import com.ricardofrango.squadify.presentation.routes.appLanguage
import com.ricardofrango.squadify.coach.config.CoachConfig
import com.ricardofrango.squadify.coach.domain.HandleGoogleNotificationUseCase
import com.ricardofrango.squadify.coach.domain.VerifyGooglePurchaseUseCase
import com.ricardofrango.squadify.model.GooglePurchaseRequest
import com.ricardofrango.squadify.security.userId
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receiveText
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.ktor.ext.inject
import java.security.MessageDigest
import kotlin.io.encoding.Base64

/**
 * Payments in the stores (REQUIREMENTS §5c; Google Play first). `POST /plans/purchases/google?language=`:
 * the owner's purchase, checked with Google, answered with the plans. `POST /store/google/notifications`:
 * Google's real-time developer notifications, pushed by a Pub/Sub subscription to this URL with
 * `?token=` (GOOGLE_PLAY_NOTIFICATIONS_TOKEN); anything else is refused.
 */
fun Route.storeRoutes() {
    val verifyGooglePurchase by inject<VerifyGooglePurchaseUseCase>()
    val handleGoogleNotification by inject<HandleGoogleNotificationUseCase>()

    authenticate("auth-jwt") {
        post("/plans/purchases/google") {
            val userId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
            call.respond(verifyGooglePurchase(userId, call.receive<GooglePurchaseRequest>(), call.appLanguage()))
        }
    }

    post("/store/google/notifications") {
        val expected = CoachConfig.googlePlayNotificationsToken
        val given = call.request.queryParameters["token"].orEmpty()
        if (expected.isEmpty() || !MessageDigest.isEqual(expected.toByteArray(), given.toByteArray())) {
            return@post call.respond(HttpStatusCode.Forbidden)
        }
        val notification = googleNotification(call.receiveText())
            ?: return@post call.respond(HttpStatusCode.NoContent)
        if (notification.packageName == CoachConfig.googlePlayPackageName) {
            notification.purchaseToken?.let { handleGoogleNotification(it) }
        }
        // Answered once handled: anything but a 2xx makes Pub/Sub send it again.
        call.respond(HttpStatusCode.NoContent)
    }
}

/** A Pub/Sub push's developer notification: its app and, for a subscription's, the purchase token. */
internal data class GoogleNotification(val packageName: String?, val purchaseToken: String?)

internal fun googleNotification(body: String): GoogleNotification? = runCatching {
    val data = Json.parseToJsonElement(body).jsonObject["message"]?.jsonObject?.get("data")?.jsonPrimitive?.contentOrNull ?: return null
    val decoded = Json.parseToJsonElement(Base64.decode(data).decodeToString()).jsonObject
    GoogleNotification(
        packageName = decoded["packageName"]?.jsonPrimitive?.contentOrNull,
        purchaseToken = decoded["subscriptionNotification"]?.jsonObject?.get("purchaseToken")?.jsonPrimitive?.contentOrNull
    )
}.getOrNull()
