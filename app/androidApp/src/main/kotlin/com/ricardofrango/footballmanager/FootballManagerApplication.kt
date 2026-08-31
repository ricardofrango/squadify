package com.ricardofrango.footballmanager

import android.app.Application
import com.ricardofrango.footballmanager.di.initKoin
import org.koin.android.ext.koin.androidContext

class FootballManagerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@FootballManagerApplication)
        }
    }
}
