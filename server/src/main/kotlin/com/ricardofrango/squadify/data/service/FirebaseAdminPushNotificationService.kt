package com.ricardofrango.squadify.data.service

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.AndroidNotification
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.MulticastMessage
import com.google.firebase.messaging.Notification
import com.ricardofrango.squadify.config.ServerConfig
import com.ricardofrango.squadify.domain.service.PushNotificationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream

/**
 * Sends via the Firebase Admin SDK, reusing the same Firebase project (squadify-958d3) already
 * registered for Android (Crashlytics/App Distribution/FCM -- see TECHNICAL_DECISIONS.md §12 and the
 * push-notifications section). [FirebaseApp] is process-wide singleton state, so initialization is
 * guarded by [FirebaseApp.getApps] rather than relying solely on this class's own single-instance
 * Koin registration.
 */
class FirebaseAdminPushNotificationService : PushNotificationService {
    private val logger = LoggerFactory.getLogger(javaClass)

    init {
        if (FirebaseApp.getApps().isEmpty() && ServerConfig.firebaseServiceAccountJson.isNotBlank()) {
            try {
                val credentials = GoogleCredentials.fromStream(
                    ByteArrayInputStream(ServerConfig.firebaseServiceAccountJson.toByteArray())
                )
                FirebaseApp.initializeApp(FirebaseOptions.builder().setCredentials(credentials).build())
            } catch (e: Exception) {
                logger.error("Failed to initialize FirebaseApp -- push notifications will be skipped", e)
            }
        }
    }

    override suspend fun sendClubMembershipAdded(tokens: List<String>, clubName: String) {
        send(
            tokens = tokens,
            // Mirrors SquadifyMessagingService.NOTIFICATION_CHANNEL_INVITES on the Android client --
            // see TECHNICAL_DECISIONS.md's push-notifications section.
            channelId = "invites",
            title = "Squadify",
            body = "You've been added to $clubName"
        )
    }

    override suspend fun sendAgeGroupStaffAdded(tokens: List<String>, ageGroupName: String, clubName: String) {
        send(
            tokens = tokens,
            // Mirrors SquadifyMessagingService.NOTIFICATION_CHANNEL_AGE_GROUP on the Android client --
            // see TECHNICAL_DECISIONS.md's push-notifications section.
            channelId = "age_group",
            title = "Squadify",
            body = "You've been added as staff to $ageGroupName at $clubName"
        )
    }

    private suspend fun send(tokens: List<String>, channelId: String, title: String, body: String) {
        if (tokens.isEmpty()) return
        if (ServerConfig.firebaseServiceAccountJson.isBlank() || FirebaseApp.getApps().isEmpty()) {
            logger.warn("FIREBASE_SERVICE_ACCOUNT_JSON not set -- skipping push to {} device(s) ({})", tokens.size, title)
            return
        }
        try {
            val message = MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .setAndroidConfig(
                    AndroidConfig.builder()
                        .setNotification(AndroidNotification.builder().setChannelId(channelId).build())
                        .build()
                )
                .build()
            // The Admin SDK's send calls block on real HTTP I/O -- hop off whatever dispatcher the
            // calling use case is on rather than tying up a request coroutine.
            val response = withContext(Dispatchers.IO) {
                FirebaseMessaging.getInstance().sendEachForMulticast(message)
            }
            if (response.failureCount > 0) {
                logger.error("Push send to {} had {} failure(s)", title, response.failureCount)
            }
        } catch (e: Exception) {
            logger.error("Push send threw", e)
        }
    }
}
