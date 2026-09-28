package com.ricardofrango.squadify.domain.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The one channel a background piece of code (the authenticated HTTP client's 401 handling, see
 * `Koin.kt`) has to force the whole app back to the Login screen -- there is no other "the session is
 * dead, log out" path reachable from outside a Composable/ViewModel. `AppNavigation` collects
 * [forceLogout] for the lifetime of the app and clears the entire back stack to `Screen.Login` whenever
 * it fires, regardless of which screen is currently showing.
 */
class SessionEventBus {
    private val _forceLogout = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val forceLogout: SharedFlow<Unit> = _forceLogout.asSharedFlow()

    suspend fun emitForceLogout() {
        _forceLogout.emit(Unit)
    }
}
