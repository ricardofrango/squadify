package com.sportssoftw.squadcall.coach

import com.sportssoftw.squadcall.coach.di.initCoachKoin
import com.sportssoftw.squadcall.data.config.DefaultAppConfig

/**
 * Called from Swift (app/coachIosApp's iOSApp.swift), which picks [baseUrl] and [isDebug] from the build
 * configuration (Debug, Staging, Release). No push token yet: the coach app isn't registered with
 * Firebase on iOS (REQUIREMENTS Phase 9, launch readiness).
 *
 * Not named `initCoachKoinIOS`: Kotlin/Native would export a function starting with "init" to Swift as
 * `doInit…` (the clubs app's `startKoinIOS`, TECHNICAL_DECISIONS "Environment-Based App Config").
 */
fun startCoachKoinIOS(baseUrl: String, isDebug: Boolean, appVersion: String?, buildNumber: Int) {
    // The build number (CFBundleVersion) is sent with each request so an old build can be refused (TECHNICAL_DECISIONS §85).
    initCoachKoin(appConfig = DefaultAppConfig(baseUrl = baseUrl, isDebug = isDebug, appVersion = appVersion, platform = "ios", buildNumber = buildNumber, storeUrl = "https://apps.apple.com"))
}
