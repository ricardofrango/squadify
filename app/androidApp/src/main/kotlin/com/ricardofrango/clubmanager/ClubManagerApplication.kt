package com.ricardofrango.clubmanager

import android.app.Application
import com.ricardofrango.clubmanager.di.initKoin
import org.koin.android.ext.koin.androidContext

class ClubManagerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@ClubManagerApplication)
        }
    }
}
