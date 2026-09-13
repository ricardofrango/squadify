package com.ricardofrango.squadify

import android.app.Application
import com.ricardofrango.squadify.di.initKoin
import org.koin.android.ext.koin.androidContext

class SquadifyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@SquadifyApplication)
        }
    }
}
