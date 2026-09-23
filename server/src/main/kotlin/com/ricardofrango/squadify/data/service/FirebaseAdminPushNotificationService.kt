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
import com.ricardofrango.squadify.model.MatchScore
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
            channelId = INVITES_CHANNEL,
            title = "Squadify",
            body = "You've been added to $clubName"
        )
    }

    override suspend fun sendAgeGroupStaffAdded(tokens: List<String>, ageGroupName: String, clubName: String) {
        send(
            tokens = tokens,
            channelId = AGE_GROUP_CHANNEL,
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
            // A single MulticastMessage tops out at 500 tokens -- club-wide live-match pushes are the
            // one caller here that can plausibly exceed that, so this chunks rather than assuming the
            // association-push callers' small lists forever stay the only ones.
            tokens.chunked(500).forEach { chunk ->
                val message = MulticastMessage.builder()
                    .addAllTokens(chunk)
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
            }
        } catch (e: Exception) {
            logger.error("Push send threw", e)
        }
    }

    override suspend fun sendMatchScheduled(tokens: List<String>, teamName: String, opponentName: String) {
        send(
            tokens = tokens,
            channelId = AGE_GROUP_CHANNEL,
            title = "Squadify",
            body = "New match: $teamName vs $opponentName has been scheduled"
        )
    }

    override suspend fun sendMatchRescheduled(tokens: List<String>, teamName: String, opponentName: String) {
        send(
            tokens = tokens,
            channelId = AGE_GROUP_CHANNEL,
            title = "Squadify",
            body = "$teamName's match against $opponentName has been updated"
        )
    }

    override suspend fun sendMatchCancelled(tokens: List<String>, teamName: String, opponentName: String) {
        send(
            tokens = tokens,
            channelId = AGE_GROUP_CHANNEL,
            title = "Squadify",
            body = "$teamName's match against $opponentName has been cancelled"
        )
    }

    override suspend fun sendMatchKickedOff(tokens: List<String>, teamName: String, opponentName: String) {
        send(
            tokens = tokens,
            channelId = LIVE_MATCHES_CHANNEL,
            title = "Squadify",
            body = "$teamName kicked off against $opponentName"
        )
    }

    override suspend fun sendHalfTime(tokens: List<String>, teamName: String, opponentName: String, score: MatchScore) {
        send(
            tokens = tokens,
            channelId = LIVE_MATCHES_CHANNEL,
            title = "Squadify",
            body = "Half-time: $teamName ${score.ourScore} - ${score.opponentScore} $opponentName"
        )
    }

    override suspend fun sendSecondHalfStarted(tokens: List<String>, teamName: String, opponentName: String) {
        send(
            tokens = tokens,
            channelId = LIVE_MATCHES_CHANNEL,
            title = "Squadify",
            body = "Second half under way: $teamName vs $opponentName"
        )
    }

    override suspend fun sendMatchFinished(tokens: List<String>, teamName: String, opponentName: String, score: MatchScore) {
        send(
            tokens = tokens,
            channelId = LIVE_MATCHES_CHANNEL,
            title = "Squadify",
            body = "Full-time: $teamName ${score.ourScore} - ${score.opponentScore} $opponentName"
        )
    }

    override suspend fun sendGoalScored(tokens: List<String>, teamName: String, opponentName: String, score: MatchScore) {
        send(
            tokens = tokens,
            channelId = LIVE_MATCHES_CHANNEL,
            title = "Squadify",
            body = "Goal! $teamName ${score.ourScore} - ${score.opponentScore} $opponentName"
        )
    }

    private companion object {
        // Mirror SquadifyMessagingService's NOTIFICATION_CHANNEL_* constants on the Android client --
        // see TECHNICAL_DECISIONS.md's push-notifications section.
        const val INVITES_CHANNEL = "invites"
        const val AGE_GROUP_CHANNEL = "age_group"
        const val LIVE_MATCHES_CHANNEL = "live_matches"
    }
}
