package com.ricardofrango.squadify.data.service

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.MulticastMessage
import com.ricardofrango.squadify.config.ServerConfig
import com.ricardofrango.squadify.domain.service.GoalPush
import com.ricardofrango.squadify.domain.service.PushNotificationService
import com.ricardofrango.squadify.model.MatchScore
import com.ricardofrango.squadify.model.PushLink
import com.ricardofrango.squadify.model.PushMessage
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
 * Sends no literal message text at all -- only a [PushMessage]: a string resource *key* and its
 * ordered arguments, which the client resolves against its own device-locale resources, the same
 * mechanism the rest of the app already uses for EN/PT -- see TECHNICAL_DECISIONS.md §27. It goes as
 * FCM data only, never a notification payload: the app always draws the notification itself, which is
 * what lets a match's pushes be one chat-style notification per match (§56).
 */
/**
 * [clubLogoUrl] gives the public address of a club's logo (or null), shown as the notification's image.
 */
class FirebaseAdminPushNotificationService(
    private val clubLogoUrl: suspend (clubId: String) -> String? = { null }
) : PushNotificationService {
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
        send(tokens, PushMessage(link, INVITES_CHANNEL, "push_club_membership_added", listOf(clubName)))
    }

    override suspend fun sendAgeGroupStaffAdded(tokens: List<String>, link: PushLink, ageGroupName: String, clubName: String) {
        send(tokens, PushMessage(link, AGE_GROUP_CHANNEL, "push_age_group_staff_added", listOf(ageGroupName, clubName)))
    }

    override suspend fun sendMatchScheduled(tokens: List<String>, link: PushLink, teamName: String, opponentName: String) {
        send(tokens, matchMessage(link, AGE_GROUP_CHANNEL, "push_match_scheduled", listOf(teamName, opponentName), teamName, opponentName))
    }

    override suspend fun sendMatchRescheduled(tokens: List<String>, link: PushLink, teamName: String, opponentName: String) {
        send(tokens, matchMessage(link, AGE_GROUP_CHANNEL, "push_match_rescheduled", listOf(teamName, opponentName), teamName, opponentName))
    }

    override suspend fun sendMatchCancelled(tokens: List<String>, link: PushLink, teamName: String, opponentName: String) {
        send(tokens, matchMessage(link, AGE_GROUP_CHANNEL, "push_match_cancelled", listOf(teamName, opponentName), teamName, opponentName))
    }

    override suspend fun sendMatchKickedOff(tokens: List<String>, link: PushLink, teamName: String, opponentName: String) {
        send(tokens, matchMessage(link, LIVE_MATCHES_CHANNEL, "push_match_kicked_off", listOf(teamName, opponentName), teamName, opponentName))
    }

    override suspend fun sendHalfTime(tokens: List<String>, link: PushLink, teamName: String, opponentName: String, score: MatchScore) {
        send(tokens, matchMessage(link, LIVE_MATCHES_CHANNEL, "push_half_time", scoreArgs(teamName, opponentName, score), teamName, opponentName))
    }

    override suspend fun sendSecondHalfStarted(tokens: List<String>, link: PushLink, teamName: String, opponentName: String) {
        send(tokens, matchMessage(link, LIVE_MATCHES_CHANNEL, "push_second_half_started", listOf(teamName, opponentName), teamName, opponentName))
    }

    override suspend fun sendMatchFinished(tokens: List<String>, link: PushLink, teamName: String, opponentName: String, score: MatchScore) {
        send(tokens, matchMessage(link, LIVE_MATCHES_CHANNEL, "push_match_finished", scoreArgs(teamName, opponentName, score), teamName, opponentName))
    }

    override suspend fun sendGoalScored(tokens: List<String>, link: PushLink, teamName: String, opponentName: String, score: MatchScore, goal: GoalPush) {
        send(tokens, goalMessage(link, teamName, opponentName, score, goal))
    }

    override suspend fun sendTeamCreated(tokens: List<String>, link: PushLink, teamNames: List<String>, ageGroupName: String, clubName: String) {
        if (teamNames.size == 1) {
            send(tokens, PushMessage(link, AGE_GROUP_CHANNEL, "push_team_created_one", listOf(teamNames.first(), ageGroupName)))
        } else {
            send(tokens, PushMessage(link, AGE_GROUP_CHANNEL, "push_team_created_many", listOf(teamNames.joinToString(", "), ageGroupName)))
        }
    }

    private suspend fun send(tokens: List<String>, message: PushMessage) {
        if (tokens.isEmpty()) return
        if (ServerConfig.firebaseServiceAccountJson.isBlank() || FirebaseApp.getApps().isEmpty()) {
            logger.warn("FIREBASE_SERVICE_ACCOUNT_JSON not set -- skipping push to {} device(s) ({})", tokens.size, message.bodyKey)
            return
        }
        // Our team's lines and every other push carry the club's logo (§57); a scorer or opponent line
        // carries its own picture, or none (the app then shows their initial). Best effort: without it
        // the push still goes, just without the picture.
        val needsClubLogo = message.imageUrl == null && (message.thread?.sender ?: PushMessage.Sender.TEAM) == PushMessage.Sender.TEAM
        val withImage = if (!needsClubLogo) message else try {
            message.copy(imageUrl = clubLogoUrl(message.link.clubId))
        } catch (e: Exception) {
            logger.warn("Couldn't look up club {}'s logo for a push", message.link.clubId, e)
            message
        }
        try {
            // A single MulticastMessage tops out at 500 tokens -- club-wide live-match pushes are the
            // one caller here that can plausibly exceed that, so this chunks rather than assuming the
            // association-push callers' small lists forever stay the only ones.
            tokens.chunked(500).forEach { chunk ->
                val multicast = MulticastMessage.builder()
                    .addAllTokens(chunk)
                    // Data only, no notification payload: the app always draws it (so a match's pushes
                    // can be one notification), and high priority so it's delivered at once, even
                    // while the phone dozes.
                    .putAllData(withImage.toData())
                    .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())
                    .build()
                // The Admin SDK's send calls block on real HTTP I/O -- hop off whatever dispatcher the
                // calling use case is on rather than tying up a request coroutine.
                val response = withContext(Dispatchers.IO) {
                    FirebaseMessaging.getInstance().sendEachForMulticast(multicast)
                }
                if (response.failureCount > 0) {
                    logger.error("Push send of {} had {} failure(s)", message.bodyKey, response.failureCount)
                }
            }
        } catch (e: Exception) {
            logger.error("Push send threw", e)
        }
    }

    internal companion object {
        // Mirror SquadifyMessagingService's NOTIFICATION_CHANNEL_* constants on the Android client --
        // see TECHNICAL_DECISIONS.md's push-notifications section.
        const val INVITES_CHANNEL = "invites"
        const val AGE_GROUP_CHANNEL = "age_group"
        const val LIVE_MATCHES_CHANNEL = "live_matches"

        // FCM data values are strings -- every Android string resource these name must use %N$s for every
        // placeholder, including the score numbers, or getString(id, *args) throws a format mismatch.
        fun scoreArgs(teamName: String, opponentName: String, score: MatchScore) =
            listOf(teamName, score.ourScore.toString(), score.opponentScore.toString(), opponentName)

        /** A line in the match's notification from our team ("Sub-13 A vs Fontelas"). */
        fun matchMessage(link: PushLink, channelId: String, bodyKey: String, args: List<String>, teamName: String, opponentName: String) =
            PushMessage(link, channelId, bodyKey, args, thread = PushMessage.MatchThread(teamName, opponentName, PushMessage.Sender.TEAM, teamName))

        /**
         * "Goal! Sub-13 A 2 - 1 Fontelas", from whoever's goal it was: our scorer (with their photo), the
         * opponent (with their logo), or our team when nobody's credited (an own goal, the score box).
         */
        fun goalMessage(link: PushLink, teamName: String, opponentName: String, score: MatchScore, goal: GoalPush): PushMessage {
            // A local: GoalPush lives in :server:core, so its property can't be smart cast here.
            val scorerName = goal.scorerName
            val (sender, senderName) = when {
                !goal.forUs -> PushMessage.Sender.OPPONENT to opponentName
                scorerName != null -> PushMessage.Sender.SCORER to scorerName
                else -> PushMessage.Sender.TEAM to teamName
            }
            return PushMessage(
                link, LIVE_MATCHES_CHANNEL, "push_goal_scored", scoreArgs(teamName, opponentName, score),
                imageUrl = goal.imageUrl,
                thread = PushMessage.MatchThread(teamName, opponentName, sender, senderName)
            )
        }
    }
}
