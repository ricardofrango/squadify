package com.ricardofrango.squadify.data.service

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.AndroidNotification
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.MulticastMessage
import com.ricardofrango.squadify.config.ServerConfig
import com.ricardofrango.squadify.domain.service.PushNotificationService
import com.ricardofrango.squadify.model.MatchScore
import com.ricardofrango.squadify.model.PushLink
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
 *
 * Sends no literal message text at all -- only a body-localization *key* (matching an Android string
 * resource name) and its ordered arguments, via [AndroidNotification]'s own localization fields.
 * The client resolves the key against its own device-locale resources, the same mechanism the rest of
 * the app already uses for EN/PT -- see TECHNICAL_DECISIONS.md §27. The title is omitted entirely for
 * the same reason: [com.ricardofrango.squadify]'s Android client already falls back to its own
 * (correctly per-flavor-named) app name locally when no title is sent.
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

    override suspend fun sendClubMembershipAdded(tokens: List<String>, link: PushLink, clubName: String) {
        send(tokens, link, INVITES_CHANNEL, "push_club_membership_added", listOf(clubName))
    }

    override suspend fun sendAgeGroupStaffAdded(tokens: List<String>, link: PushLink, ageGroupName: String, clubName: String) {
        send(tokens, link, AGE_GROUP_CHANNEL, "push_age_group_staff_added", listOf(ageGroupName, clubName))
    }

    override suspend fun sendMatchScheduled(tokens: List<String>, link: PushLink, teamName: String, opponentName: String) {
        send(tokens, link, AGE_GROUP_CHANNEL, "push_match_scheduled", listOf(teamName, opponentName))
    }

    override suspend fun sendMatchRescheduled(tokens: List<String>, link: PushLink, teamName: String, opponentName: String) {
        send(tokens, link, AGE_GROUP_CHANNEL, "push_match_rescheduled", listOf(teamName, opponentName))
    }

    override suspend fun sendMatchCancelled(tokens: List<String>, link: PushLink, teamName: String, opponentName: String) {
        send(tokens, link, AGE_GROUP_CHANNEL, "push_match_cancelled", listOf(teamName, opponentName))
    }

    override suspend fun sendMatchKickedOff(tokens: List<String>, link: PushLink, teamName: String, opponentName: String) {
        send(tokens, link, LIVE_MATCHES_CHANNEL, "push_match_kicked_off", listOf(teamName, opponentName))
    }

    override suspend fun sendHalfTime(tokens: List<String>, link: PushLink, teamName: String, opponentName: String, score: MatchScore) {
        send(tokens, link, LIVE_MATCHES_CHANNEL, "push_half_time", scoreArgs(teamName, opponentName, score))
    }

    override suspend fun sendSecondHalfStarted(tokens: List<String>, link: PushLink, teamName: String, opponentName: String) {
        send(tokens, link, LIVE_MATCHES_CHANNEL, "push_second_half_started", listOf(teamName, opponentName))
    }

    override suspend fun sendMatchFinished(tokens: List<String>, link: PushLink, teamName: String, opponentName: String, score: MatchScore) {
        send(tokens, link, LIVE_MATCHES_CHANNEL, "push_match_finished", scoreArgs(teamName, opponentName, score))
    }

    override suspend fun sendGoalScored(tokens: List<String>, link: PushLink, teamName: String, opponentName: String, score: MatchScore) {
        send(tokens, link, LIVE_MATCHES_CHANNEL, "push_goal_scored", scoreArgs(teamName, opponentName, score))
    }

    override suspend fun sendTeamCreated(tokens: List<String>, link: PushLink, teamNames: List<String>, ageGroupName: String, clubName: String) {
        if (teamNames.size == 1) {
            send(tokens, link, AGE_GROUP_CHANNEL, "push_team_created_one", listOf(teamNames.first(), ageGroupName))
        } else {
            send(tokens, link, AGE_GROUP_CHANNEL, "push_team_created_many", listOf(teamNames.joinToString(", "), ageGroupName))
        }
    }

    // FCM localization args are always strings on the wire (AndroidNotification.addBodyLocalizationArg
    // only takes String) -- every Android string resource this maps to must use %N$s for every
    // placeholder, including the score numbers, or getString(id, *args) throws a format mismatch.
    private fun scoreArgs(teamName: String, opponentName: String, score: MatchScore) =
        listOf(teamName, score.ourScore.toString(), score.opponentScore.toString(), opponentName)

    private suspend fun send(tokens: List<String>, link: PushLink, channelId: String, bodyLocKey: String, bodyLocArgs: List<String>) {
        if (tokens.isEmpty()) return
        if (ServerConfig.firebaseServiceAccountJson.isBlank() || FirebaseApp.getApps().isEmpty()) {
            logger.warn("FIREBASE_SERVICE_ACCOUNT_JSON not set -- skipping push to {} device(s) ({})", tokens.size, bodyLocKey)
            return
        }
        try {
            // A single MulticastMessage tops out at 500 tokens -- club-wide live-match pushes are the
            // one caller here that can plausibly exceed that, so this chunks rather than assuming the
            // association-push callers' small lists forever stay the only ones.
            tokens.chunked(500).forEach { chunk ->
                val message = MulticastMessage.builder()
                    .addAllTokens(chunk)
                    // Where tapping it goes (§56). Data rides along with the notification: Android
                    // hands it to the app as the launch intent's extras when it shows the notification
                    // itself (app in the background), or to onMessageReceived otherwise.
                    .putAllData(link.toData())
                    .setAndroidConfig(
                        AndroidConfig.builder()
                            .setNotification(
                                AndroidNotification.builder()
                                    .setChannelId(channelId)
                                    .setBodyLocalizationKey(bodyLocKey)
                                    .addAllBodyLocalizationArgs(bodyLocArgs)
                                    .build()
                            )
                            .build()
                    )
                    .build()
                // The Admin SDK's send calls block on real HTTP I/O -- hop off whatever dispatcher the
                // calling use case is on rather than tying up a request coroutine.
                val response = withContext(Dispatchers.IO) {
                    FirebaseMessaging.getInstance().sendEachForMulticast(message)
                }
                if (response.failureCount > 0) {
                    logger.error("Push send of {} had {} failure(s)", bodyLocKey, response.failureCount)
                }
            }
        } catch (e: Exception) {
            logger.error("Push send threw", e)
        }
    }

    private companion object {
        // Mirror SquadifyMessagingService's NOTIFICATION_CHANNEL_* constants on the Android client --
        // see TECHNICAL_DECISIONS.md's push-notifications section.
        const val INVITES_CHANNEL = "invites"
        const val AGE_GROUP_CHANNEL = "age_group"
        const val LIVE_MATCHES_CHANNEL = "live_matches"
    }
}
