package com.ricardofrango.squadify.coach.android

import com.ricardofrango.squadify.push.FirebasePushTokenProvider
import com.ricardofrango.squadify.push.PushNotifications
import android.app.Application
import com.ricardofrango.squadify.coach.di.initCoachKoin
import com.ricardofrango.squadify.data.config.DefaultAppConfig
import org.koin.android.ext.koin.androidContext

class CoachApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val appConfig = DefaultAppConfig(
            baseUrl = BuildConfig.BASE_URL,
            isDebug = BuildConfig.DEBUG,
            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        )
        initCoachKoin(appConfig = appConfig, pushTokenProvider = FirebasePushTokenProvider()) {
            androidContext(this@CoachApplication)
        }
        // Pushes from the coach server: a tap opens CoachActivity, which hands the link to the navigation.
        PushNotifications.configure(this, CoachActivity::class.java, R.drawable.ic_notification)
    }
}
