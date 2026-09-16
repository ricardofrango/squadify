package com.ricardofrango.squadify

import android.app.Application
import com.ricardofrango.squadify.data.config.DefaultAppConfig
import com.ricardofrango.squadify.di.initKoin
import org.koin.android.ext.koin.androidContext

class SquadifyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val appConfig = DefaultAppConfig(baseUrl = BuildConfig.BASE_URL, isDebug = BuildConfig.DEBUG)
        initKoin(appConfig = appConfig) {
            androidContext(this@SquadifyApplication)
        }
    }
}
