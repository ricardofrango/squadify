package com.ricardofrango.squadify

import android.app.Application
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import com.ricardofrango.squadify.data.config.DefaultAppConfig
import com.ricardofrango.squadify.di.initKoin
import com.ricardofrango.squadify.push_notifications.FirebasePushTokenProvider
import com.ricardofrango.squadify.push_notifications.SquadifyMessagingService
import org.koin.android.ext.koin.androidContext

class SquadifyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val appConfig = DefaultAppConfig(
            baseUrl = BuildConfig.BASE_URL,
            isDebug = BuildConfig.DEBUG,
            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        )
        initKoin(appConfig = appConfig, pushTokenProvider = FirebasePushTokenProvider()) {
            androidContext(this@SquadifyApplication)
        }
        createNotificationChannels()
    }

    // Required on API 26+ before any notification can be shown -- see SquadifyMessagingService,
    // which picks one of these per message (falling back to NOTIFICATION_CHANNEL_DEFAULT). Safe to
    // call on every launch: creating an already-existing channel is a no-op.
    // NotificationManagerCompat/NotificationChannelCompat handle the pre-O no-op internally, so unlike
    // the raw platform APIs this needs no manual Build.VERSION.SDK_INT check.
    private fun createNotificationChannels() {
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
                .setName(getString(names.first))
                .setDescription(getString(names.second))
                .build()
        }
        NotificationManagerCompat.from(this).createNotificationChannelsCompat(channels)
    }
}
