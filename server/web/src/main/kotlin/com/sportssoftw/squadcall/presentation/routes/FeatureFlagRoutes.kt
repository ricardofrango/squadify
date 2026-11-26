package com.sportssoftw.squadcall.presentation.routes

import com.sportssoftw.squadcall.model.FeatureFlags
import com.sportssoftw.squadcall.presentation.AppMinimums
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * The feature flags the apps read when they start (`GET /feature-flags`, under /v1; TECHNICAL_DECISIONS §82). Public: a
 * flag isn't a secret, and the app asks before anyone has signed in. The flags come from this server's configuration.
 */
fun Route.featureFlagRoutes(flags: FeatureFlags, minimums: AppMinimums = AppMinimums()) {
    // updateRequired is about the asking app's build: the request's headers (TECHNICAL_DECISIONS §85).
    get("/feature-flags") { call.respond(flags.copy(updateRequired = minimums.requiresUpdate(call.request.headers))) }
}
