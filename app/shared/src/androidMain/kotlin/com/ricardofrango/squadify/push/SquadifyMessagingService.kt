package com.ricardofrango.squadify.push

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ricardofrango.squadify.app.shared.R
import com.ricardofrango.squadify.domain.usecase.RegisterFcmTokenUseCase
import com.ricardofrango.squadify.model.PushMessage
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.random.Random

/**
 * Android system entry point for FCM, for both apps (TECHNICAL_DECISIONS §72) -- instantiated by the OS,
 * not Koin, so dependencies come via [org.koin.core.component.KoinComponent] rather than constructor
 * injection (Koin is already started in the app's Application.onCreate by the time this could ever fire).
 * What differs per app comes from [PushNotifications]: the activity that opens and the small icon; the
 * title is the app's own name.
 *
 * Our pushes come as FCM data only ([PushMessage]), never a notification payload, so this always
 * draws them itself, open or not -- which is what lets a match's pushes be one chat-style notification
 * per match ([showMatchLine]). Each carries a [com.ricardofrango.squadify.model.PushLink], what tapping
 * it opens (the club, age group, team or match), passed on to the app's activity ([PushNotifications.launchActivity]) as intent extras.
 *
 * The channel and the text come from the server: a string resource *key* (an entry in
 * [BODY_LOCALIZATION_KEYS], matching a resource in `res/values` / `res/values-pt`) and its ordered
 * arguments, resolved against the device's own locale -- the same mechanism the rest of the app uses
 * for EN/PT (TECHNICAL_DECISIONS.md §27); never literal text. A push that isn't one of ours (a manual
 * Firebase console test) is shown from its own notification payload, or dropped without text.
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
        // Ours come as data only (PushMessage), so this runs whether or not the app is open. Anything
        // else -- a manual Firebase console test -- falls back to its own notification payload.
        val push = PushMessage.fromData(message.data)
        if (push == null) {
            showPlain(message)
            return
        }
        val body = resolveLocalized(push.bodyKey, push.bodyArgs.toTypedArray()) ?: return
        val picture = push.imageUrl?.let { downloadImage(Uri.parse(it)) }
        val thread = push.thread
        val matchId = push.link.matchId
        if (thread != null && matchId != null) {
            showMatchLine(push, thread, matchId, body, picture)
        } else {
            val builder = NotificationCompat.Builder(this, push.channelId)
                .setSmallIcon(PushNotifications.smallIcon)
                .setContentTitle(appName())
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(openIntent(Random.nextInt(), message.data))
            // The club's logo, on the right (§57).
            picture?.let { builder.setLargeIcon(it) }
            notify(Random.nextInt(), builder)
        }
    }

    /**
     * A match's pushes are one chat-style notification per match, "Sub-13 A vs Fontelas", each event a
     * line from whoever it's about: our team (the club's logo), our scorer (their photo) or the opponent
     * (their logo); without a picture Android shows the sender's initial. A new event adds a line to the
     * notification on screen, read back from it, so nothing is stored; once swiped away, the next event
     * starts a new one. Tapping opens the match.
     */
    private fun showMatchLine(push: PushMessage, thread: PushMessage.MatchThread, matchId: String, body: String, picture: Bitmap?) {
        val notificationId = matchId.hashCode()
        val showing = getSystemService(NotificationManager::class.java)
            ?.activeNotifications?.firstOrNull { it.id == notificationId }?.notification
        val style = showing?.let { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it) }
            ?: NotificationCompat.MessagingStyle(Person.Builder().setName(appName()).setKey("me").build())
        style.conversationTitle = getString(R.string.push_match_thread_title, thread.teamName, thread.opponentName)
        style.isGroupConversation = true
        // Some launchers draw a sender's picture as it comes, square, so a scorer's photo is cut to a
        // circle here; without one, the pitch's head-and-shoulders placeholder, not Android's initial.
        val avatar = when (thread.sender) {
            PushMessage.Sender.SCORER -> picture?.let(::circleCropped) ?: personPlaceholder()
            else -> picture
        }
        val sender = Person.Builder()
            .setName(thread.senderName)
            .setKey("${thread.sender}:${thread.senderName}")
            .apply { avatar?.let { setIcon(IconCompat.createWithBitmap(it)) } }
            .build()
        style.addMessage(NotificationCompat.MessagingStyle.Message(body, System.currentTimeMillis(), sender))

        val builder = NotificationCompat.Builder(this, push.channelId)
            .setSmallIcon(PushNotifications.smallIcon)
            .setStyle(style)
            .setAutoCancel(true)
            .setContentIntent(openIntent(notificationId, push.toData()))
        // The conversation's own picture: our club in front of the opponent, the app's badge on it (the
        // developer, 2026-11-06). Without it Android made one from the senders, with a blank circle for
        // the notification's own "me", and our club's logo twice.
        builder.setLargeIcon(matchPicture(thread))
        notify(notificationId, builder)
    }

    /**
     * Our club's logo in front (bottom left) and the opponent's behind it (top right), each on a white
     * disc, a gap cut round the front one, as Android draws a group's two faces. A side without a logo
     * gets its initial on a grey disc.
     */
    private fun matchPicture(thread: PushMessage.MatchThread): Bitmap {
        val ours = thread.teamImageUrl?.let { downloadImage(Uri.parse(it)) }
        val theirs = thread.opponentImageUrl?.let { downloadImage(Uri.parse(it)) }
        val size = AVATAR_SIZE.toFloat()
        val face = size * 0.66f
        val output = Bitmap.createBitmap(AVATAR_SIZE, AVATAR_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val back = RectF(size - face, 0f, size, face)
        val front = RectF(0f, size - face, face, size)
        drawFace(canvas, back, theirs, thread.opponentName)
        // The gap: clear a slightly bigger circle where the front face goes.
        val gap = size * 0.035f
        canvas.drawCircle(front.centerX(), front.centerY(), face / 2 + gap, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        })
        drawFace(canvas, front, ours, thread.teamName)
        return output
    }

    /** A logo fitted inside a white disc at [bounds], or [name]'s initial on a grey one. */
    private fun drawFace(canvas: Canvas, bounds: RectF, logo: Bitmap?, name: String) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.color = if (logo != null) 0xFFFFFFFF.toInt() else PLACEHOLDER_DISC
        canvas.drawCircle(bounds.centerX(), bounds.centerY(), bounds.width() / 2, paint)
        if (logo != null) {
            // Inside the disc's inner square, keeping the logo's proportions.
            val inner = bounds.width() * 0.62f
            val scale = minOf(inner / logo.width, inner / logo.height)
            val w = logo.width * scale
            val h = logo.height * scale
            val dst = RectF(bounds.centerX() - w / 2, bounds.centerY() - h / 2, bounds.centerX() + w / 2, bounds.centerY() + h / 2)
            canvas.drawBitmap(logo, null, dst, paint)
        } else {
            paint.color = PLACEHOLDER_FIGURE
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = bounds.width() * 0.42f
            paint.isFakeBoldText = true
            val initial = name.trim().firstOrNull()?.uppercase() ?: "?"
            canvas.drawText(initial, bounds.centerX(), bounds.centerY() - (paint.descent() + paint.ascent()) / 2, paint)
        }
    }

    /** A push that isn't one of ours, as its own payload says; dropped without text. */
    private fun showPlain(message: RemoteMessage) {
        val notification = message.notification ?: return
        val body = resolveLocalized(notification.bodyLocalizationKey, notification.bodyLocalizationArgs) ?: notification.body ?: return
        val builder = NotificationCompat.Builder(this, notification.channelId ?: NOTIFICATION_CHANNEL_DEFAULT)
            .setSmallIcon(PushNotifications.smallIcon)
            .setContentTitle(notification.title ?: appName())
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(openIntent(Random.nextInt(), message.data))
        notification.imageUrl?.let(::downloadImage)?.let { builder.setLargeIcon(it) }
        notify(Random.nextInt(), builder)
    }

    /**
     * Opens the app where the push points: its data carries the PushLink (§58), which the activity reads
     * back from these extras. [requestCode] keeps one notification's PendingIntent (and extras) its own.
     */
    private fun openIntent(requestCode: Int, data: Map<String, String>): PendingIntent {
        val intent = Intent(this, PushNotifications.launchActivity)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        data.forEach { (key, value) -> intent.putExtra(key, value) }
        return PendingIntent.getActivity(this, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** The app's own name, as on its icon ("Squadify", "Squadify Coach Dev"). */
    private fun appName(): String = applicationInfo.loadLabel(packageManager).toString()

    private fun notify(id: Int, builder: NotificationCompat.Builder) {
        // No buttons Android makes up from the text (it read an opponent's name as a place and offered
        // "Open Maps" on a match's notification): ours only open the app where they point.
        builder.setAllowSystemGeneratedContextualActions(false)
        val notificationManager = NotificationManagerCompat.from(this)
        // areNotificationsEnabled() covers both "user disabled notifications for this app" and (on
        // API 33+) "POST_NOTIFICATIONS was never granted" -- the officially recommended guard for
        // NotificationManagerCompat.notify(), which lint requires a check for on API 33+.
        if (notificationManager.areNotificationsEnabled()) {
            notificationManager.notify(id, builder.build())
        }
    }

    /** [source] centre-cropped to a square and cut to a circle (transparent outside it). */
    private fun circleCropped(source: Bitmap): Bitmap {
        val side = minOf(source.width, source.height)
        val output = Bitmap.createBitmap(AVATAR_SIZE, AVATAR_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawCircle(AVATAR_SIZE / 2f, AVATAR_SIZE / 2f, AVATAR_SIZE / 2f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        val left = (source.width - side) / 2
        val top = (source.height - side) / 2
        canvas.drawBitmap(source, Rect(left, top, left + side, top + side), Rect(0, 0, AVATAR_SIZE, AVATAR_SIZE), paint)
        return output
    }

    /**
     * A player without a photo: the same head-and-shoulders placeholder as on the pitch and in player
     * rows (PersonPlaceholder in the shared UI), in the same grey.
     */
    private fun personPlaceholder(): Bitmap {
        val w = AVATAR_SIZE.toFloat()
        val output = Bitmap.createBitmap(AVATAR_SIZE, AVATAR_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = PLACEHOLDER_DISC
        canvas.drawCircle(w / 2, w / 2, w / 2, paint)
        canvas.save()
        canvas.clipPath(Path().apply { addOval(RectF(0f, 0f, w, w), Path.Direction.CW) })
        paint.color = PLACEHOLDER_FIGURE
        canvas.drawCircle(w / 2, w * 0.4f, w * 0.19f, paint)
        canvas.drawOval(RectF(w * 0.17f, w * 0.66f, w * 0.83f, w * 1.28f), paint)
        canvas.restore()
        return output
    }

    /**
     * The notification's image (a club logo, public), or null if it can't be fetched quickly. This runs
     * on FCM's background thread, which may block, but only for a few seconds.
     */
    private fun downloadImage(uri: android.net.Uri): Bitmap? = try {
        val connection = URL(uri.toString()).openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        try {
            connection.inputStream.use(BitmapFactory::decodeStream)
        } finally {
            connection.disconnect()
        }
    } catch (e: Exception) {
        null
    }

    // Resource IDs are compile-time constants, not lookupable by the loc key's own string value without
    // Resources.getIdentifier() -- slow, and flagged by lint as fragile -- so this map is the bridge,
    // kept in sync by hand with both FirebaseAdminPushNotificationService's key literals and the string
    // resources they name. Returns null (never throws) for an unrecognized key or an arg-count mismatch, so callers fall back to the
    // message's own literal text rather than dropping the notification.
    private fun resolveLocalized(key: String?, args: Array<String>?): String? {
        val resId = BODY_LOCALIZATION_KEYS[key ?: return null] ?: return null
        return try {
            getString(resId, *(args ?: emptyArray()))
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val NOTIFICATION_CHANNEL_DEFAULT = "default"

        // A sender's picture on a match's notification, in pixels.
        private const val AVATAR_SIZE = 192
        // The pitch's PlaceholderGray, and its 40% disc as it shows on white -- opaque, so it reads on a
        // dark notification shade too.
        private const val PLACEHOLDER_FIGURE = 0xFF8E8E93.toInt()
        private const val PLACEHOLDER_DISC = 0xFFD2D2D4.toInt()
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
            "push_call_up_ready" to R.string.push_call_up_ready,
            "push_staff_joined" to R.string.push_staff_joined,
        )
    }
}
