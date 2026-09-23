package com.ricardofrango.squadify.push_notifications

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ricardofrango.squadify.MainActivity
import com.ricardofrango.squadify.R
import com.ricardofrango.squadify.domain.usecase.RegisterFcmTokenUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.random.Random

/**
 * Android system entry point for FCM -- instantiated by the OS, not Koin, so dependencies come via
 * [org.koin.core.component.KoinComponent] rather than constructor injection (Koin is already started in
 * [com.ricardofrango.squadify.SquadifyApplication.onCreate] by the time this could ever fire).
 *
 * Only handles the "notification" message payload for now -- there's no structured "data" payload
 * contract yet for deep-linking a tapped notification anywhere other than opening the app to wherever
 * it was last (see ISSUES TO FIX.md).
 *
 * Which channel a notification lands in is decided by the sender, not here: FCM's own
 * `notification.android_channel_id` payload field round-trips as [RemoteMessage.Notification.channelId].
 * Falls back to [NOTIFICATION_CHANNEL_DEFAULT] for anything that doesn't set one (a manual test push
 * from the Firebase console, for instance, can't set this field).
 *
 * The body text is resolved locally, never taken as literal text from the server: the server sends a
 * `body_loc_key` (matching an entry in [BODY_LOCALIZATION_KEYS], which itself matches a string resource
 * name in `res/values`, or a locale-specific variant such as `res/values-pt`) plus ordered
 * `body_loc_args`, and this resolves that against whichever locale's resources the device is actually
 * running -- the same mechanism the rest of the app already uses for EN/PT, applied to notifications
 * too (see TECHNICAL_DECISIONS.md §27). Falls back to the message's own literal `body` (or drops the
 * notification) for anything without a recognized key -- a manual Firebase console test push, for
 * instance, can't set `body_loc_key` either.
 */
class SquadifyMessagingService : FirebaseMessagingService(), KoinComponent {

    private val registerFcmTokenUseCase: RegisterFcmTokenUseCase by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Best-effort, same as RegisterCurrentFcmTokenUseCase's reasoning -- a failed registration
        // here (e.g. offline, or fired before the user is logged in at all, in which case the server
        // call 401s) isn't actionable from a background callback, and login/session-restore already
        // re-registers the current token as its own step, so nothing is lost by just swallowing this.
        scope.launch {
            try {
                registerFcmTokenUseCase(token)
            } catch (e: Exception) {
                // See above.
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val notification = message.notification ?: return
        val title = notification.title ?: getString(R.string.app_name)
        val body = resolveLocalizedBody(notification) ?: notification.body ?: return

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, notification.channelId ?: NOTIFICATION_CHANNEL_DEFAULT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)

        val notificationManager = NotificationManagerCompat.from(this)
        // areNotificationsEnabled() covers both "user disabled notifications for this app" and (on
        // API 33+) "POST_NOTIFICATIONS was never granted" -- the officially recommended guard for
        // NotificationManagerCompat.notify(), which lint requires a check for on API 33+.
        if (notificationManager.areNotificationsEnabled()) {
            notificationManager.notify(Random.nextInt(), notificationBuilder.build())
        }
    }

    // Resource IDs are compile-time constants, not lookupable by the loc key's own string value without
    // Resources.getIdentifier() -- slow, and flagged by lint as fragile -- so this map is the bridge,
    // kept in sync by hand with both FirebaseAdminPushNotificationService's key literals and the string
    // resources they name. Returns null (never throws) for an unrecognized key or an arg-count mismatch,
    // so callers fall back to the message's own literal body rather than dropping the notification.
    private fun resolveLocalizedBody(notification: RemoteMessage.Notification): String? {
        val key = notification.bodyLocalizationKey ?: return null
        val resId = BODY_LOCALIZATION_KEYS[key] ?: return null
        val args = notification.bodyLocalizationArgs ?: emptyArray()
        return try {
            getString(resId, *args)
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val NOTIFICATION_CHANNEL_DEFAULT = "default"
        const val NOTIFICATION_CHANNEL_LIVE_MATCHES = "live_matches"
        const val NOTIFICATION_CHANNEL_AGE_GROUP = "age_group"
        const val NOTIFICATION_CHANNEL_INVITES = "invites"

        private val BODY_LOCALIZATION_KEYS = mapOf(
            "push_club_membership_added" to R.string.push_club_membership_added,
            "push_age_group_staff_added" to R.string.push_age_group_staff_added,
            "push_match_scheduled" to R.string.push_match_scheduled,
            "push_match_rescheduled" to R.string.push_match_rescheduled,
            "push_match_cancelled" to R.string.push_match_cancelled,
            "push_match_kicked_off" to R.string.push_match_kicked_off,
            "push_half_time" to R.string.push_half_time,
            "push_second_half_started" to R.string.push_second_half_started,
            "push_match_finished" to R.string.push_match_finished,
            "push_goal_scored" to R.string.push_goal_scored,
            "push_team_created_one" to R.string.push_team_created_one,
            "push_team_created_many" to R.string.push_team_created_many,
        )
    }
}
