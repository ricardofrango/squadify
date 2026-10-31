package com.ricardofrango.squadify

import android.app.Application
import com.ricardofrango.squadify.data.config.DefaultAppConfig
import com.ricardofrango.squadify.di.initKoin
import com.ricardofrango.squadify.push.FirebasePushTokenProvider
import com.ricardofrango.squadify.push.PushNotifications
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
        PushNotifications.configure(this, MainActivity::class.java, R.drawable.ic_notification)
    }
}
