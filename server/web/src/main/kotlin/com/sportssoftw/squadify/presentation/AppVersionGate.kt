package com.sportssoftw.squadify.presentation

import com.sportssoftw.squadify.model.APP_BUILD_HEADER
import com.sportssoftw.squadify.model.APP_PLATFORM_HEADER
import com.sportssoftw.squadify.model.ErrorResponse
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.request.path
import io.ktor.server.response.respond

/**
 * The oldest app build each platform may use (MIN_ANDROID_BUILD, MIN_IOS_BUILD; TECHNICAL_DECISIONS §85). 0 is no
 * minimum. A request without the headers (web, desktop, the other server, tools) is never refused.
 */
data class AppMinimums(val android: Int = 0, val ios: Int = 0) {
    /** Whether a request with [headers] comes from an app build below its platform's minimum. */
    fun requiresUpdate(headers: Headers): Boolean {
        val platform = headers[APP_PLATFORM_HEADER]?.trim()?.lowercase() ?: return false
        val build = headers[APP_BUILD_HEADER]?.trim()?.toIntOrNull() ?: return false
        val minimum = when (platform) {
            "android" -> android
            "ios" -> ios
            else -> 0
        }
        return minimum > 0 && build < minimum
    }
}

/** Paths an out-of-date app can still call: the flags, which tell it to update (and the app reads them first). */
private val exemptPaths = setOf("/v1/feature-flags")

class AppVersionGateConfig {
    var minimums: AppMinimums = AppMinimums()
}

/**
 * Refuses every request from an app build below its minimum with 426 Upgrade Required (`APP_001`), before routing,
 * so each endpoint and the live game's socket refuse it alike. Installed by `installSquadifyApi`.
 */
val AppVersionGate = createApplicationPlugin(name = "AppVersionGate", createConfiguration = ::AppVersionGateConfig) {
    val minimums = pluginConfig.minimums
    application.intercept(ApplicationCallPipeline.Plugins) {
        val call = context
        if (call.request.path().trimEnd('/') in exemptPaths) return@intercept
        if (minimums.requiresUpdate(call.request.headers)) {
            call.respond(HttpStatusCode.UpgradeRequired, ErrorResponse("This app is out of date: update it to keep using Squadify", "APP_001"))
            finish()
        }
    }
}
