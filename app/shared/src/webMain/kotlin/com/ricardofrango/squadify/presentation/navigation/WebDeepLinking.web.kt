package com.ricardofrango.squadify.presentation.navigation

import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

// The browser history binding Navigation 2 had built in (bindToBrowserNavigation, which Navigation 3
// doesn't have yet), for the app's own back stack. Each browser history entry holds the whole back
// stack as its state, one route per line, so Back and Forward restore exactly what was showing.

actual suspend fun AppNavigator.bindToBrowserHistoryIfWeb() {
    val window = browserWindow()
    val appAddress = window.location.origin + window.location.pathname
    coroutineScope {
        launch {
            window.popStateEvents().collect { event ->
                val state = event.state
                if (state == null) {
                    // An address typed or pasted while the app is open: open that screen on top.
                    screenFromRoute(window.location.hash)?.let { navigate(it) }
                    return@collect
                }
                val screens = state.lines().mapNotNull(::screenFromRoute)
                restoreBackStack(screens)
            }
        }
        launch {
            snapshotFlow { backStack.map { it.screen.toRoute() } }.collect { routes ->
                if (routes.isEmpty()) return@collect
                val state = routes.joinToString("\n")
                val address = appAddress + "#" + routes.last()
                when (window.history.state) {
                    // The first screen, or the state the browser just restored (Back/Forward): only the address.
                    null, state -> window.history.replaceState(state, "", address)
                    // A move inside the app: a new browser history entry.
                    else -> window.history.pushState(state, "", address)
                }
            }
        }
    }
}

actual fun initialDeepLinkRouteOrNull(): String? {
    val window = browserWindow()
    val hash = window.location.hash
    if (hash.isEmpty()) return null
    // Consumed exactly once: cleared from the visible address, and opened by Splash once signed in.
    window.history.replaceState(null, "", window.location.pathname)
    return hash.removePrefix("#").takeIf { it.isNotBlank() }
}

private fun BrowserWindow.popStateEvents(): Flow<BrowserPopStateEvent> = callbackFlow {
    val callback: (BrowserEvent) -> Unit = { event ->
        @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
        trySend(event as BrowserPopStateEvent)
    }
    addEventListener("popstate", callback)
    awaitClose { removeEventListener("popstate", callback) }
}

// Minimal hand-rolled interop, as navigation-runtime's own (internal) BrowserWindow: kotlin-wrappers'
// typed DOM bindings aren't published for wasmJs.
internal external interface BrowserLocation {
    val origin: String
    val pathname: String
    val hash: String
}

internal external interface BrowserHistory {
    val state: String?
    fun pushState(data: String?, title: String, url: String?)
    fun replaceState(data: String?, title: String, url: String?)
}

internal external interface BrowserEvent
internal external interface BrowserPopStateEvent : BrowserEvent {
    val state: String?
}

internal external interface BrowserWindow {
    val location: BrowserLocation
    val history: BrowserHistory
    fun addEventListener(type: String, callback: ((BrowserEvent) -> Unit)?)
    fun removeEventListener(type: String, callback: ((BrowserEvent) -> Unit)?)
}

internal expect fun browserWindow(): BrowserWindow
