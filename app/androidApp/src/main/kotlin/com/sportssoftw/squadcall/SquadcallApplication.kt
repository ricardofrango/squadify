package com.sportssoftw.squadcall

import android.app.Application
import com.sportssoftw.squadcall.data.config.DefaultAppConfig
import com.sportssoftw.squadcall.di.initKoin
import com.sportssoftw.squadcall.push.FirebasePushTokenProvider
import com.sportssoftw.squadcall.push.PushNotifications
import org.koin.android.ext.koin.androidContext

class SquadcallApplication : Application() {
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
            androidContext(this@SquadcallApplication)
        }
        PushNotifications.configure(this, MainActivity::class.java, R.drawable.ic_notification)
    }
}
