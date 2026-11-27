package com.sportssoftw.squadcall

import com.sportssoftw.squadcall.data.config.DefaultAppConfig
import com.sportssoftw.squadcall.di.initKoin

/** Called from Swift (iOSApp.swift), which picks [baseUrl]/[isDebug] via `#if DEBUG` -- Xcode's own
 * Debug/Release build configurations, already present in every Xcode project. See
 * TECHNICAL_DECISIONS.md's "Environment-Based App Config" section.
 *
 * Deliberately not named `initKoinIOS` -- Kotlin/Native's Objective-C interop generator prefixes any
 * exported function starting with "init" with "do" (avoids colliding with Objective-C's special
 * initializer semantics), so the Swift-visible name would silently become `doInitKoinIOS(...)`. */
fun startKoinIOS(baseUrl: String, isDebug: Boolean, buildNumber: Int) {
    // The build number (CFBundleVersion) is sent with each request so an old build can be refused (TECHNICAL_DECISIONS §85).
    initKoin(appConfig = DefaultAppConfig(baseUrl = baseUrl, isDebug = isDebug, platform = "ios", buildNumber = buildNumber, storeUrl = "https://apps.apple.com"))
}
