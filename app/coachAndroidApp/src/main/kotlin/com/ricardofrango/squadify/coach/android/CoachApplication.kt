package com.ricardofrango.squadify.coach.android

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
        initCoachKoin(appConfig = appConfig) {
            androidContext(this@CoachApplication)
        }
    }
}
