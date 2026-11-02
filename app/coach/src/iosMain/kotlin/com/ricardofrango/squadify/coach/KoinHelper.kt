package com.ricardofrango.squadify.coach

import com.ricardofrango.squadify.coach.di.initCoachKoin
import com.ricardofrango.squadify.data.config.DefaultAppConfig

/**
 * Called from Swift (app/coachIosApp's iOSApp.swift), which picks [baseUrl] and [isDebug] from the build
 * configuration (Debug, Staging, Release). No push token yet: the coach app isn't registered with
 * Firebase on iOS (REQUIREMENTS Phase 9, launch readiness).
 *
 * Not named `initCoachKoinIOS`: Kotlin/Native would export a function starting with "init" to Swift as
 * `doInit…` (the clubs app's `startKoinIOS`, TECHNICAL_DECISIONS "Environment-Based App Config").
 */
fun startCoachKoinIOS(baseUrl: String, isDebug: Boolean, appVersion: String?) {
    initCoachKoin(appConfig = DefaultAppConfig(baseUrl = baseUrl, isDebug = isDebug, appVersion = appVersion))
}
