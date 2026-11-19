package com.sportssoftw.squadify

import android.app.Application
import com.sportssoftw.squadify.data.config.DefaultAppConfig
import com.sportssoftw.squadify.di.initKoin
import com.sportssoftw.squadify.push.FirebasePushTokenProvider
import com.sportssoftw.squadify.push.PushNotifications
import org.koin.android.ext.koin.androidContext

class SquadifyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val appConfig = DefaultAppConfig(
            baseUrl = BuildConfig.BASE_URL,
            isDebug = BuildConfig.DEBUG,
            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            platform = "android",
            buildNumber = BuildConfig.VERSION_CODE,
            storeUrl = "https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}"
        )
        initKoin(appConfig = appConfig, pushTokenProvider = FirebasePushTokenProvider()) {
            androidContext(this@SquadifyApplication)
        }
        PushNotifications.configure(this, MainActivity::class.java, R.drawable.ic_notification)
    }
}
