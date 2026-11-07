package com.ricardofrango.squadify.push

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ricardofrango.squadify.app.shared.R
import com.ricardofrango.squadify.model.PushLink
import com.ricardofrango.squadify.presentation.navigation.PushLinkInbox

/**
 * Push notifications on Android, for both apps (TECHNICAL_DECISIONS §72). Each app calls [configure] in
 * its Application.onCreate with what's its own, and its activity calls [takePushLink] and shows
 * [RequestAppPermissions]; the receiving is [SquadifyMessagingService].
 */
object PushNotifications {
    /** The activity a tapped notification opens (the app's only one). */
    lateinit var launchActivity: Class<out Activity>
        private set

    /** The status bar icon. */
    @DrawableRes
    var smallIcon: Int = 0
        private set

    fun configure(context: Context, launchActivity: Class<out Activity>, @DrawableRes smallIcon: Int) {
        this.launchActivity = launchActivity
        this.smallIcon = smallIcon
        createChannels(context)
    }

    /**
     * Required on API 26+ before any notification can be shown -- [SquadifyMessagingService] picks one of
     * these per message (falling back to [SquadifyMessagingService.NOTIFICATION_CHANNEL_DEFAULT]). Safe to
     * call on every launch: creating an already-existing channel is a no-op. NotificationManagerCompat/
     * NotificationChannelCompat handle the pre-O no-op internally, so this needs no SDK_INT check.
     */
    private fun createChannels(context: Context) {
        val channels = listOf(
            Triple(
                SquadifyMessagingService.NOTIFICATION_CHANNEL_LIVE_MATCHES,
                R.string.notification_channel_live_matches_name to R.string.notification_channel_live_matches_description,
                // Live match events are time-sensitive -- worth a heads-up/sound, unlike the others.
                NotificationManagerCompat.IMPORTANCE_HIGH
            ),
            Triple(
                SquadifyMessagingService.NOTIFICATION_CHANNEL_AGE_GROUP,
                R.string.notification_channel_age_group_name to R.string.notification_channel_age_group_description,
                NotificationManagerCompat.IMPORTANCE_DEFAULT
            ),
            Triple(
                SquadifyMessagingService.NOTIFICATION_CHANNEL_INVITES,
                R.string.notification_channel_invites_name to R.string.notification_channel_invites_description,
                NotificationManagerCompat.IMPORTANCE_DEFAULT
            ),
            Triple(
                SquadifyMessagingService.NOTIFICATION_CHANNEL_DEFAULT,
                R.string.notification_channel_default_name to R.string.notification_channel_default_description,
                NotificationManagerCompat.IMPORTANCE_DEFAULT
            )
        ).map { (id, names, importance) ->
            NotificationChannelCompat.Builder(id, importance)
                .setName(context.getString(names.first))
                .setDescription(context.getString(names.second))
                .build()
        }
        NotificationManagerCompat.from(context).createNotificationChannelsCompat(channels)
    }

    /**
     * A tapped push notification's link (§58), from the intent's extras: Android puts the message's
     * data there when it shows the notification itself (app in the background), and
     * SquadifyMessagingService copies it there when the app shows it. Removed afterwards so the same
     * intent isn't opened twice.
     */
    fun takePushLink(intent: Intent?, inbox: PushLinkInbox) {
        val extras = intent?.extras ?: return
        val link = PushLink.fromData(PushLink.KEYS.associateWith { extras.getString(it) }) ?: return
        PushLink.KEYS.forEach(intent::removeExtra)
        inbox.post(link)
    }
}

/**
 * The permissions the app asks for when it opens, in one request (Android shows one permission dialog at
 * a time, and a second request made meanwhile is refused at once):
 * - POST_NOTIFICATIONS, a runtime permission on API 33+ (declaring it in the manifest alone doesn't
 *   grant it). Without it FCM registration and delivery still work, but SquadifyMessagingService's
 *   notify() is a silent no-op (see its areNotificationsEnabled() guard), so nothing ever shows.
 * - ACCESS_LOCAL_NETWORK, Android 17's permission for reaching devices on the same network, only where
 *   the app declares it: the dev builds, which talk to the server on the developer's machine over Wi-Fi.
 *   A fresh install lost it, and the app then couldn't reach the server (the developer, 2026-11-07).
 *   Staging and production talk to public addresses and don't declare it.
 * Asked on every launch; the system remembers a grant or a denial and doesn't ask again once decided.
 */
@Composable
fun RequestAppPermissions() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    LaunchedEffect(Unit) {
        val wanted = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT >= ANDROID_17 && declares(context, LOCAL_NETWORK)) add(LOCAL_NETWORK)
        }
        val missing = wanted.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) launcher.launch(missing.toTypedArray())
    }
}

/** Whether the app's manifest asks for [permission] (the dev builds' local network one). */
private fun declares(context: Context, permission: String): Boolean = try {
    context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        .requestedPermissions?.contains(permission) == true
} catch (e: PackageManager.NameNotFoundException) {
    false
}

// Android 17's local network permission, by name: the SDK constants the app compiles against may not have it.
private const val LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
private const val ANDROID_17 = 37
