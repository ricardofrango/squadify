package com.ricardofrango.squadify.presentation.navigation

import androidx.navigation.ExperimentalBrowserHistoryApi
import androidx.navigation.NavHostController
import androidx.navigation.bindToBrowserNavigation

@OptIn(ExperimentalBrowserHistoryApi::class)
actual suspend fun NavHostController.bindToBrowserHistoryIfWeb() {
    bindToBrowserNavigation()
}

actual fun initialDeepLinkRouteOrNull(): String? {
    val window = browserWindow()
    val hash = window.location.hash
    if (hash.isEmpty()) return null

    // Consumed exactly once: strip it from the visible URL so bindToBrowserNavigation's own default
    // "navigate to the initial hash" behavior (see BrowserHistory.kt in navigation-runtime) finds
    // nothing left to act on once it binds a moment later.
    window.history.replaceState(null, "", window.location.pathname)

    return decodeURIComponent(hash.removePrefix("#")).takeIf { it.isNotBlank() }
}

// Minimal hand-rolled interop, kept identical to the js actual on purpose -- kotlin-wrappers' typed DOM
// bindings aren't published for wasmJs (see Platform.js.kt vs. Platform.wasmJs.kt for the same split
// elsewhere in this module) -- mirrors what navigation-runtime's own (internal, so unusable from here
// directly) BrowserWindow/refBrowserWindow does, including its exact `js("window")` access pattern.
private external interface BrowserWindow {
    val location: BrowserLocation
    val history: BrowserHistory
}

private external interface BrowserLocation {
    val hash: String
    val pathname: String
}

private external interface BrowserHistory {
    fun replaceState(data: String?, title: String, url: String?)
}

private fun browserWindow(): BrowserWindow = js("window")
private external fun decodeURIComponent(str: String): String
