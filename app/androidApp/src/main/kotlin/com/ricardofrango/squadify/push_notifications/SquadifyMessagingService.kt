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
 * Only handles the "notification" message payload (title/body) for now -- there's no structured "data"
 * payload contract yet, since the server doesn't send pushes at all today (this branch only built
 * registration; sending is a later commit). Tapping a notification just opens [com.ricardofrango.squadify.MainActivity]; deep
 * linking to a specific club/match/etc. needs that data contract to exist first.
 *
 * Which channel a notification lands in is decided by the sender, not here: FCM's own
 * `notification.android_channel_id` payload field round-trips as [RemoteMessage.Notification.channelId],
 * so the server just needs to set it per notification type once it actually sends pushes. Falls back to
 * [NOTIFICATION_CHANNEL_DEFAULT] for anything that doesn't set one (a manual test push from the Firebase
 * console, for instance, can't set this field).
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
        val body = notification.body ?: return

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )

        // TODO: R.mipmap.ic_launcher is a full-color launcher icon, not a proper monochrome status-bar
        // icon -- Android will render it degraded (a white silhouette/blob) rather than the intended
        // mark. Swap for a real single-color vector notification icon; tracked in ISSUES TO FIX.md.
        val notificationBuilder = NotificationCompat.Builder(this, notification.channelId ?: NOTIFICATION_CHANNEL_DEFAULT)
            .setSmallIcon(R.mipmap.ic_launcher)
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

    companion object {
        const val NOTIFICATION_CHANNEL_DEFAULT = "default"
        const val NOTIFICATION_CHANNEL_LIVE_MATCHES = "live_matches"
        const val NOTIFICATION_CHANNEL_AGE_GROUP = "age_group"
        const val NOTIFICATION_CHANNEL_INVITES = "invites"
    }
}